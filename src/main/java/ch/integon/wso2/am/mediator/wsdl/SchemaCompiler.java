package ch.integon.wso2.am.mediator.wsdl;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import java.io.StringWriter;

import javax.wsdl.WSDLException;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.BusException;
import org.apache.cxf.service.model.SchemaInfo;
import org.apache.cxf.staxutils.StaxUtils;
import org.apache.ws.commons.schema.XmlSchema;
import org.apache.ws.commons.schema.XmlSchemaExternal;
import org.codehaus.stax2.validation.XMLValidationSchema;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import com.ctc.wstx.msv.W3CMultiSchemaFactory;

import ch.integon.wso2.am.mediator.wsdl.model.SOAPServiceOperation;
import ch.integon.wso2.am.mediator.wsdl.model.SOAPValidationException;

/**
 * Compiles XML schema(s) for a given cxf service and operation.
 */
public class SchemaCompiler
{
	private static final Log logger = LogFactory.getLog(SchemaCompiler.class);

	/**
	 * 
	 * @param serviceOperation service and operation to build the schema from
	 * @return XMLValidationSchema ready for validation
	 * @throws BusException            if CXF bus fails
	 * @throws XMLStreamException      if XML reading fails
	 * @throws WSDLException           if WSDL parsing fails
	 * @throws SOAPValidationException if schema compilation fails
	 */
	public XMLValidationSchema compileSchema(SOAPServiceOperation serviceOperation)
			throws SOAPValidationException, XMLStreamException
	{
		logger.debug("Starting schema compilation for given services");

		// Collect schema sources
		Map<String, Source> sources = new TreeMap<>();
		for (SchemaInfo schemaInfo : serviceOperation.getService().getSchemas())
		{
			XmlSchema schema = schemaInfo.getSchema();
			if (XMLConstants.W3C_XML_SCHEMA_NS_URI.equals(schema.getSourceURI()))
			{
				logger.debug("Skipping default XML schema namespace: " + schema.getSourceURI());
				continue;
			}

			List<?> externals;
			try
			{
				Method getExternalsMethod = schema.getClass().getMethod("getExternals");
				externals = (List<?>) getExternalsMethod.invoke(schema);
			} catch (Exception e)
			{
				throw new RuntimeException("Failed to get schema externals via reflection", e);
			}

			// Handle schemas without targetNamespace but with externals
			if (schema.getTargetNamespace() == null && !externals.isEmpty())
			{
				logger.debug("Schema without targetNamespace has externals, processing them");
				for (Object o : externals)
				{
					XmlSchemaExternal external = (XmlSchemaExternal) o;
					addSchema(sources, external.getSchema(), getElement(external.getSchema().getSourceURI()));
				}
			}
			// Normal schema with targetNamespace
			else if (schema.getTargetNamespace() != null)
			{
				logger.debug("Adding schema with targetNamespace: " + schema.getTargetNamespace());
				addSchema(sources, schema, schemaInfo.getElement());
			} else
			{
				throw new IllegalStateException("Schema without targetNamespace and no externals");
			}
		}

		// Compile all collected schemas into a single validation schema
		logger.debug("Compiling collected schemas into XMLValidationSchema");
		if (logger.isDebugEnabled())
		{
			logger.debug("Schema sources defined:");
			for (String k : sources.keySet())
			{
				logger.debug("Source-Name: " + k);
				TransformerFactory tf = TransformerFactory.newInstance();
				try
				{
					Transformer transformer = tf.newTransformer();
					transformer.setOutputProperty(OutputKeys.INDENT, "yes");
					transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
					
					StringWriter writer = new StringWriter();
					StreamResult result = new StreamResult(writer);

					transformer.transform(sources.get(k), result);
					
					logger.debug("Schema content:\n" + writer.toString());
				} catch (Exception e)
				{
					logger.debug("Exception during schema resources transformation", e);
				}
			}
		}

		W3CMultiSchemaFactory factory = new W3CMultiSchemaFactory();
		XMLValidationSchema compiledSchema = null;
		try
		{
			compiledSchema = factory.createSchema(null, sources);
		} catch (Exception e)
		{
			throw new SOAPValidationException("error while reading the schema", e);
		}

		logger.debug("Schema compilation completed successfully");
		return compiledSchema;
	}

	/**
	 * Adds an individual schema to the source map for compilation.
	 */
	private void addSchema(Map<String, Source> sources, XmlSchema schema, Element element)
	{
		String systemId = schema.getSourceURI() != null ? schema.getSourceURI() : schema.getTargetNamespace();
		sources.put(schema.getTargetNamespace(), new DOMSource(element, systemId));
		logger.debug("Added schema to sources: " + schema.getTargetNamespace() + ", systemId: " + systemId);
	}

	/**
	 * Reads an XML document from the given path/URI and returns its root element.
	 */
	private Element getElement(String path) throws XMLStreamException
	{
		logger.debug("Reading XML element from path: " + path);
		InputSource in = new InputSource(path);
		Document doc = StaxUtils.read(in);
		return doc.getDocumentElement();
	}
}