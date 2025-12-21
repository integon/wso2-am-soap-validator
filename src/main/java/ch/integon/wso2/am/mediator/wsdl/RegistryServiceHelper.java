package ch.integon.wso2.am.mediator.wsdl;

import java.net.URI;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.registry.core.Collection;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.service.RegistryService;
import org.wso2.carbon.registry.core.session.UserRegistry;

/**
 * Helper class for accessing WSO2 governance registry. Provides methods to
 * obtain the governance registry, download resources, and get the latest WSDL
 * URI for an API.
 */
public class RegistryServiceHelper
{

	private static final Log logger = LogFactory.getLog(RegistryServiceHelper.class);

	/** The governance registry instance retrieved from OSGi context */
	private final UserRegistry governanceRegistry;

	/**
	 * Initializes the helper by fetching the governance registry from the OSGi
	 * Carbon context.
	 * 
	 * @throws RegistryException if the registry service is not available or cannot
	 *                           be initialized
	 */
	public RegistryServiceHelper() throws RegistryException
	{
		logger.debug("Fetching RegistryService from OSGi context");
		PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
		RegistryService registryService = (RegistryService) carbonContext.getOSGiService(RegistryService.class, null);
		if (registryService == null)
		{
			logger.error("RegistryService not available in OSGi context");
			throw new RegistryException("RegistryService not available in OSGi context");
		}

		logger.debug("Obtaining governance system registry");
		this.governanceRegistry = registryService.getGovernanceSystemRegistry();
		logger.debug("Governance registry initialized successfully");
	}

	/**
	 * Returns the governance registry instance.
	 * 
	 * @return UserRegistry for governance system
	 */
	public UserRegistry getGovernanceRegistry()
	{
		return governanceRegistry;
	}

	/**
	 * Retrieves the latest WSDL URI for a given API UUID. Checks the registry for
	 * WSDL files or archives and delegates extraction to WSDLExtractor.
	 * 
	 * @param apiUUID   API identifier
	 * @param extractor Helper to extract WSDL or archive from registry
	 * @return URI pointing to the latest WSDL file
	 * @throws Exception if the API path, revision, or WSDL is missing, or
	 *                   extraction fails
	 */
	public URI[] getLatestWSDLUri(String apiUUID, WSDLExtractor extractor) throws Exception
	{
		String apiBasePath = "/apimgt/applicationdata/apis/" + apiUUID;
		logger.debug("Checking if API base path exists: " + apiBasePath);
		if (!governanceRegistry.resourceExists(apiBasePath))
		{
			logger.error("API base path does not exist: " + apiBasePath);
			throw new RegistryException("API base path does not exist: " + apiBasePath);
		}

		// Retrieve revision collection and determine latest revision
		Collection apiRevisionCollection = (Collection) governanceRegistry.get(apiBasePath);
		int latestRevision = apiRevisionCollection.getChildCount();
		logger.debug("Latest revision number for API " + apiUUID + ": " + latestRevision);

		String apiRevisionPath = apiBasePath + "/" + latestRevision;
		if (!governanceRegistry.resourceExists(apiRevisionPath))
		{
			logger.error("Revision path does not exist: " + apiRevisionPath);
			throw new RegistryException("Revision path does not exist: " + apiRevisionPath);
		}
		logger.debug("Revision path exists: " + apiRevisionPath);

		Collection revisionCollection = (Collection) governanceRegistry.get(apiRevisionPath);
		String[] items = revisionCollection.getChildren();
		logger.debug("Found registry items in revision: " + String.join(", ", items));

		// Identify WSDL file or archives folder
		String wsdlFile = null;
		String archivesFolder = null;
		for (String item : items)
		{
			if (item.toLowerCase().endsWith(".wsdl"))
			{
				wsdlFile = item;
				logger.debug("Found WSDL file: " + wsdlFile);
			} else if (item.toLowerCase().endsWith("/archives"))
			{
				archivesFolder = item;
				logger.debug("Found archives folder: " + archivesFolder);
			}
		}

		if (wsdlFile != null)
		{
			logger.debug("Delegating extraction of single WSDL to WSDLExtractor");
			return extractor.getSingleWSDLFromRegistry(governanceRegistry, wsdlFile);
		} else if (archivesFolder != null)
		{
			logger.debug("Delegating extraction of WSDL from archive to WSDLExtractor");
			return extractor.getArchiveWSDLFromRegistry(governanceRegistry, archivesFolder);
		} else
		{
			logger.error("Neither WSDL file nor archive found in registry for API: " + apiUUID);
			throw new RegistryException("Neither WSDL file nor archive found in registry for API: " + apiUUID);
		}
	}

	/**
	 * Downloads a registry resource and converts it to a byte array. Supports
	 * resources stored as byte[] or String.
	 * 
	 * @param resourcePath path of the resource in the registry
	 * @return byte array of the resource content
	 * @throws RegistryException if resource type is unsupported or retrieval fails
	 */
	public byte[] downloadResource(String resourcePath) throws RegistryException
	{
		logger.debug("Downloading registry resource: " + resourcePath);
		Resource resource = governanceRegistry.get(resourcePath);
		Object content = resource.getContent();

		if (content instanceof byte[])
		{
			logger.debug("Registry resource content type: byte[], size=" + ((byte[]) content).length);
			return (byte[]) content;
		} else if (content instanceof String)
		{
			byte[] data = ((String) content).getBytes();
			logger.debug("Registry resource content type: String, size=" + data.length);
			return data;
		} else
		{
			String typeName = (content == null ? "null" : content.getClass().getName());
			logger.error("Unsupported registry resource type: " + typeName);
			throw new RegistryException("Unsupported registry resource type: " + typeName);
		}
	}

}