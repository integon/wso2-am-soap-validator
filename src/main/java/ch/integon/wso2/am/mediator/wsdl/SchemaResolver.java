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

		List<ServiceInfo> services;
		if (apiServices.containsKey(apiUUID))
		{
			logger.debug("Cached services found for: " + apiUUID);
			services = apiServices.get(apiUUID);
		} else
		{
			services = apiServices.computeIfAbsent(apiUUID, k ->
			{
				try
				{
					logger.debug(
							"No cached services found for: " + apiUUID + " - Start loading files from the registry");

					RegistryServiceHelper registryHelper = new RegistryServiceHelper();
					logger.debug("RegistryServiceHelper initialized");

					WSDLExtractor wsdlExtractor = new WSDLExtractor();
					logger.debug("WSDLExtractor initialized");

					URI[] wsdlURIs = registryHelper.getLatestWSDLUri(apiUUID, wsdlExtractor);
					logger.debug("Obtained WSDL URIs: " + wsdlURIs);

					return serviceBuilder.buildServices(wsdlURIs);
				} catch (Exception e)
				{
					logger.error("unable to build services from wsdl", e);
				}
				return null;
			});

		}
		if (services == null || services.size() == 0)
		{
			logger.error("no service found");
			return null;
		}

		// build cache key for schema cache
		SOAPServiceOperation serviceOperation;
		try
		{
			serviceOperation = serviceBuilder.getMatchedServiceOperation(services, result.getSoapAction(),
					result.getSoapBodyElement().getQName());
		} catch (Exception e)
		{
			throw new SOAPValidationException("error while finding corresponding service and operation", e);
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
				XMLValidationSchema schema = schemaCompiler.compileSchema(serviceOperation);

				return schema;
			} catch (Exception e)
			{
				logger.error("Failed to resolve schema for API: " + apiUUID, e);
				return null;
			}
		});
		if(validationSchema == null)
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
}
