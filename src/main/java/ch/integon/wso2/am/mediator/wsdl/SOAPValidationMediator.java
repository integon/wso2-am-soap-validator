package ch.integon.wso2.am.mediator.wsdl;

import org.apache.synapse.mediators.AbstractMediator;
import org.codehaus.stax2.validation.XMLValidationProblem;
import org.codehaus.stax2.validation.XMLValidationSchema;
import org.apache.synapse.MessageContext;

import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import ch.integon.wso2.am.mediator.wsdl.model.SOAPAnalysisResult;
import ch.integon.wso2.am.mediator.wsdl.model.SOAPValidationException;

/**
 * SOAPValidationMediator is a custom class mediator for the WSO2 API Manager.
 *
 * This mediator validates incoming SOAP requests against the WSDL schema
 * defined for the API. If validation fails, a SOAP fault is returned and the
 * message processing is stopped.
 *
 * Validation handles both single WSDL files and WSDL archives (zip).
 *
 * Author: Integon GmbH
 */
public class SOAPValidationMediator extends AbstractMediator {

    private static final Log logger = LogFactory.getLog(SOAPValidationMediator.class);

    private final SOAPAnalyzer soapAnalyzer;
    private final SchemaResolver schemaResolver;
    private final SOAPValidator soapValidator;
    private final SOAPValidationFaultHandler soapValidationFaultHandler;

    public SOAPValidationMediator() {
        this.soapAnalyzer = new SOAPAnalyzer();
        this.schemaResolver = new SchemaResolver();
        this.soapValidator = new SOAPValidator();
        this.soapValidationFaultHandler = new SOAPValidationFaultHandler();
    }

    /**
     * Mediates the message by validating the SOAP payload against the WSDL schema.
     *
     * @param messageContext Synapse message context
     * @return true if payload is valid; false (or throws SynapseException) if
     *         invalid
     */
    @Override
    public boolean mediate(MessageContext messageContext) {

        // Get the API UUID from message context
        Object apiUUIDObject = messageContext.getProperty("API_UUID");
        if (apiUUIDObject == null) {
            logger.error("Cannot find API_UUID in message context - cannot validate payload");
            return false;
        }
        String apiUUID = apiUUIDObject.toString();
        logger.debug("Starting SOAP analysis for API UUID: " + apiUUID);

        // Analyze the incoming SOAP message
        SOAPAnalysisResult result = null;
		try
		{
			result = soapAnalyzer.analyze(messageContext);
		} catch (SOAPValidationException e)
		{
			return soapValidationFaultHandler.handleValidationProblem(messageContext, null, e.getMessage());
		}
        logger.debug("SOAP analysis completed. Detected SOAP version: " + result.getSoapVersion());

        // Resolve schema for this API and SOAP body
        logger.debug("Resolving schema for API UUID: " + apiUUID);
        XMLValidationSchema schema = null;
        try
        {
        	schema = schemaResolver.resolve(apiUUID, result);
        }
        catch (Exception e)
        {
        	return soapValidationFaultHandler.handleValidationProblem(messageContext, null, e.getMessage());
        }
        logger.debug("Schema resolution completed");

        // Validate SOAP payload against schema
        List<XMLValidationProblem> problems = null;
        try {
            logger.debug("Starting payload validation");
            problems = soapValidator.validate(schema, result);
            logger.debug("Payload validation completed");
        } catch (XMLStreamException e) {
            logger.error("Error during validation", e);
            
            return soapValidationFaultHandler.handleValidationProblem(messageContext, null, e.getLocalizedMessage());
        }

        // Handle schema violations 
        if ((problems != null && !problems.isEmpty())) {
            logger.error("Schema violations occured for api: " + apiUUID);

            return soapValidationFaultHandler.handleValidationProblem(messageContext, problems, "payload not conform to schema");
        }

        logger.debug("Payload is valid for API UUID: " + apiUUID);
        return true;
    }

}
