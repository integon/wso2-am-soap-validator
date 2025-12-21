package ch.integon.wso2.am.mediator.wsdl;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.registry.core.Collection;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.session.UserRegistry;

/**
 * Extracts WSDL files from the WSO2 registry, either as single files or
 * archives. Provides helper methods for ZIP extraction and file discovery.
 */
public class WSDLExtractor
{

	private static final Log logger = LogFactory.getLog(WSDLExtractor.class);

	/**
	 * Extracts a single WSDL file from the registry to a temporary directory.
	 * 
	 * @param registry the governance registry
	 * @param wsdlPath path to the WSDL file in the registry
	 * @return URI to the extracted WSDL file
	 * @throws Exception if extraction fails
	 */
	public URI[] getSingleWSDLFromRegistry(UserRegistry registry, String wsdlPath) throws Exception
	{
		logger.debug("Extracting single WSDL from registry path: " + wsdlPath);

		// Create temporary folder for WSDL
		Path tempFolder = Files.createTempDirectory("single-wsdl");
		logger.debug("Created temporary folder: " + tempFolder);

		// Download resource content
		Resource resource = registry.get(wsdlPath);
		if(resource == null)
		{
			throw new Exception("wsdl path returned null");
		}
		Object content = resource.getContent();
		byte[] wsdlData = content instanceof byte[] ? (byte[]) content : ((String) content).getBytes();
		logger.debug("Downloaded WSDL content, size: " + wsdlData.length);

		// Write WSDL to temporary file
		Path wsdlFilePath = tempFolder.resolve(extractFileNameFromPath(wsdlPath));
		Files.write(wsdlFilePath, wsdlData, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		logger.debug("WSDL written to: " + wsdlFilePath);

		return new URI[] {wsdlFilePath.toUri()};
	}

	/**
	 * Extracts a WSDL file from a ZIP archive in the registry.
	 * 
	 * @param registry    the governance registry
	 * @param archivePath path to the archive folder in the registry
	 * @return URI to the extracted WSDL file
	 * @throws Exception if extraction fails or multiple/no WSDL files are found
	 */
	public URI[] getArchiveWSDLFromRegistry(UserRegistry registry, String archivePath) throws Exception
	{
		logger.debug("Extracting WSDL from archive at registry path: " + archivePath);

		// Find the ZIP file in the registry folder
		Collection archiveCollection = (Collection) registry.get(archivePath);
		String[] items = archiveCollection.getChildren();
		String zipFilePath = null;
		for (String item : items)
		{
			if (item.toLowerCase().endsWith(".zip"))
			{
				zipFilePath = item;
				logger.debug("Found ZIP archive: " + zipFilePath);
			}
		}

		if (zipFilePath == null)
		{
			logger.error("No ZIP archive found in path: " + archivePath);
			throw new Exception("No ZIP archive found in path: " + archivePath);
		}

		// Create temporary folder for extraction
		Path tempFolder = Files.createTempDirectory("archive-wsdl");
		logger.debug("Created temporary extraction folder: " + tempFolder);

		// Download archive content
		Object content = registry.get(zipFilePath).getContent();
		byte[] archiveData = content instanceof byte[] ? (byte[]) content : ((String) content).getBytes();
		logger.debug("Downloaded archive content, size: " + archiveData.length);

		// Extract ZIP contents
		extractZIPFile(archiveData, tempFolder);
		logger.debug("ZIP extraction completed to folder: " + tempFolder);

		// Find WSDL files in extracted folder
		List<Path> wsdlFiles = findWsdlFiles(tempFolder);
		if (wsdlFiles.isEmpty())
		{
			logger.error("No WSDL files found in archive: " + zipFilePath);
			throw new Exception("No WSDL files found in archive");
		}
		
		// Create list of the found WSDL files
		List<URI> wsdlURIs = new ArrayList<URI>();
		for(Path p : wsdlFiles)
		{
			wsdlURIs.add(p.toUri());
			logger.debug("Found WSDL file in archive: " + p);
		}
		
		logger.debug("Found a total of " + wsdlURIs.size() + " wsdl files in archive");
		return wsdlURIs.toArray(new URI[wsdlURIs.size()]);
	}

	/**
	 * Extracts the contents of a ZIP archive to a target directory.
	 * 
	 * @param zipData   byte array of the ZIP file
	 * @param targetDir target directory for extraction
	 * @throws IOException if extraction fails or entries are outside the target
	 *                     directory
	 */
	private void extractZIPFile(byte[] zipData, Path targetDir) throws IOException
	{
		logger.debug("Starting ZIP extraction to: " + targetDir);

		try (ByteArrayInputStream bais = new ByteArrayInputStream(zipData);
				ZipInputStream zis = new ZipInputStream(bais))
		{

			ZipEntry entry;
			byte[] buffer = new byte[1024];

			while ((entry = zis.getNextEntry()) != null)
			{
				File newFile = targetDir.resolve(entry.getName()).toFile();
				Path normalizedPath = newFile.toPath().normalize();

				if (!normalizedPath.startsWith(targetDir))
				{
					logger.error("ZIP entry outside target directory: " + entry.getName());
					throw new IOException("ZIP entry outside target dir: " + entry.getName());
				}

				if (entry.isDirectory())
				{
					createFolder(newFile);
				} else
				{
					createFolder(newFile.getParentFile());
					try (FileOutputStream fos = new FileOutputStream(newFile))
					{
						int bytesRead;
						while ((bytesRead = zis.read(buffer)) != -1)
						{
							fos.write(buffer, 0, bytesRead);
						}
					}
					logger.debug("Extracted file from ZIP: " + newFile);
				}
			}
		}

		logger.debug("ZIP extraction completed for target directory: " + targetDir);
	}

	/**
	 * Ensures the given folder exists. Creates it if missing.
	 * 
	 * @param folder folder to create
	 * @throws IOException if folder cannot be created
	 */
	private void createFolder(File folder) throws IOException
	{
		if (!folder.exists() && !folder.mkdirs())
		{
			logger.error("Unable to create folder: " + folder);
			throw new IOException("Unable to create folder: " + folder);
		}
		if (!folder.isDirectory())
		{
			logger.error("File exists but is not a directory: " + folder);
			throw new IOException("File exists but is not a directory: " + folder);
		}
		logger.debug("Folder ensured: " + folder);
	}

	/**
	 * Finds all WSDL files in a given folder (recursively).
	 * 
	 * @param folder folder to search
	 * @return list of paths to WSDL files
	 * @throws IOException if file traversal fails
	 */
	private List<Path> findWsdlFiles(Path folder) throws IOException
	{
		// using .list to only return root level files
		try (Stream<Path> paths = Files.list(folder))
		{
			List<Path> wsdlFiles = paths.filter(Files::isRegularFile)
					.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".wsdl"))
					.collect(Collectors.toList());
			logger.debug("Found WSDL files in folder " + folder + ": " + wsdlFiles);
			return wsdlFiles;
		}
	}

	/**
	 * Extracts the file name from a path.
	 * 
	 * @param path input path string
	 * @return the file name part
	 */
	private String extractFileNameFromPath(String path)
	{
		String[] parts = path.split("/");
		String fileName = parts[parts.length - 1];
		logger.debug("Extracted file name from path '" + path + "': " + fileName);
		return fileName;
	}
}