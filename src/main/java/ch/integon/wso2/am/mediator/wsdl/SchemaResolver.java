package ch.integon.wso2.am.mediator.wsdl;

import java.net.URI;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.service.model.ServiceInfo;
import org.codehaus.stax2.validation.XMLValidationSchema;

import ch.integon.wso2.am.mediator.wsdl.model.SOAPAnalysisResult;
import ch.integon.wso2.am.mediator.wsdl.model.SOAPServiceOperation;
import ch.integon.wso2.am.mediator.wsdl.model.SOAPValidationException;

/**
 * Resolves and caches XML schemas (XMLValidationSchema) for APIs based on
 * SOAPAnalysisResult and API UUIDs.
 * <p>
 * Uses two internal caches:
 * <ul>
 * <li>{@code apiServices} – caches the CXF ServiceInfo list per API UUID</li>
 * <li>{@code schemaCache} – caches compiled XMLValidationSchema per API UUID
 * and operation</li>
 * </ul>
 * If the requested API’s services or schema are not cached, they are loaded
 * from the WSO2 governance registry and compiled.
 * <p>
 * Thread-safe: uses {@link java.util.concurrent.ConcurrentHashMap} and
 * {@code computeIfAbsent} to avoid redundant loading/compilation.
 */
public class SchemaResolver
{

	private static final Log logger = LogFactory.getLog(SchemaResolver.class);

	private static final ConcurrentHashMap<String, List<ServiceInfo>> apiServices = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<String, XMLValidationSchema> schemaCache = new ConcurrentHashMap<>();

	/**
	 * 
	 * @param apiUUID The unique identifier of the API.
	 * @param result  The SOAPAnalysisResult containing the SOAP action and body
	 *                element QName used to match the correct service operation.
	 * @return The compiled {@link XMLValidationSchema} ready for validating SOAP
	 *         messages.
	 * @throws SOAPValidationException
	 */
	public XMLValidationSchema resolve(String apiUUID, SOAPAnalysisResult result) throws SOAPValidationException
	{
		logger.debug("Start resolving XML schema for API: " + apiUUID);
		logger.debug("Looking for cached api services with api UUID: " + apiUUID);

		WSDLServiceBuilder serviceBuilder = new WSDLServiceBuilder();

		List<ServiceInfo> services = apiServices.get(apiUUID);
		if (services == null)
		{
			logger.debug("No cached services found for: " + apiUUID + " - Start loading files from the registry");
			services = loadServices(apiUUID);
		} else
		{
			logger.debug("Cached services found for: " + apiUUID);
		}

		// build cache key for schema cache
		SOAPServiceOperation serviceOperation;
		try
		{
			serviceOperation = serviceBuilder.getMatchedServiceOperation(services, result.getSoapAction(),
					result.getSoapBodyElement().getQName());
		} catch (Exception e)
		{
			throw new SOAPValidationException(
					"An error occurred while attempting to resolve the SOAP service or operation: " + e.getMessage(),
					e);
		}

		String schemaCacheKey = buildSchemaCacheKey(apiUUID, serviceOperation);
		logger.debug("Looking for cached schema with key: " + schemaCacheKey);

		// Return cached schema if exists
		if (schemaCache.containsKey(schemaCacheKey))
		{
			logger.debug("Cached schema found for " + schemaCacheKey + " - returning it");
			return schemaCache.get(schemaCacheKey);
		}

		// Compute schema if absent blocks other threads (synchronized)
		XMLValidationSchema validationSchema = schemaCache.computeIfAbsent(schemaCacheKey, k ->
		{
			try
			{
				SchemaCompiler schemaCompiler = new SchemaCompiler();
				return schemaCompiler.compileSchema(serviceOperation);
			} catch (Exception e)
			{
				logger.error("Failed to resolve schema for API: " + apiUUID, e);
				return null;
			}
		});
		if (validationSchema == null)
		{
			throw new SOAPValidationException("error during schema compilation");
		}
		return validationSchema;
	}

	/**
	 * Build a cache key based on the actual call
	 * 
	 * @param apiUUID          ID of the API called
	 * @param serviceOperation service and operation
	 * @return built cache key
	 */
	private String buildSchemaCacheKey(String apiUUID, SOAPServiceOperation serviceOperation)
	{
		return apiUUID + ":" + serviceOperation.getService().getName().toString() + ":"
				+ serviceOperation.getOperation().getName().toString();

	}

	/**
	 * Loads services corresponding to the API UUID
	 * 
	 * @param apiUUID UUID of the API
	 * @return list of ServiceInfo's of the API
	 * @throws SOAPValidationException if either no WSDL is found or unable to build Service objects from it
	 */
	private List<ServiceInfo> loadServices(String apiUUID) throws SOAPValidationException
	{
		try
		{
			RegistryServiceHelper helper = new RegistryServiceHelper();
			logger.debug("RegistryServiceHelper initialized");
			
			WSDLExtractor extractor = new WSDLExtractor();
			logger.debug("WSDLExtractor initialized");
			
			URI[] wsdlURIs = helper.getLatestWSDLUri(apiUUID, extractor);
			logger.debug("Obtained WSDL URIs: " + wsdlURIs);
			
			WSDLServiceBuilder builder = new WSDLServiceBuilder();
			List<ServiceInfo> services = builder.buildServices(wsdlURIs);
			
			if (services == null || services.isEmpty())
			{
				throw new SOAPValidationException("No WSDL service found for API: " + apiUUID);
			}
			
			return services;
			
		}
		catch (SOAPValidationException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			throw new SOAPValidationException("Unable to build services from WSDL: " + apiUUID, e);
		}
	}
}
