package ch.integon.wso2.am.mediator.wsdl;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.apache.axiom.om.OMElement;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.stax2.XMLStreamReader2;
import org.codehaus.stax2.validation.ValidationProblemHandler;
import org.codehaus.stax2.validation.XMLValidationException;
import org.codehaus.stax2.validation.XMLValidationProblem;
import org.codehaus.stax2.validation.XMLValidationSchema;

import com.ctc.wstx.stax.WstxInputFactory;

import ch.integon.wso2.am.mediator.wsdl.model.SOAPAnalysisResult;

/**
 * SOAPValidator is responsible for validating a SOAP payload against a given
 * XML schema.
 * <p>
 * It uses Woodstox XMLStreamReader2 and StAX2 validation.
 * <p>
 * Validation problems are collected into a list and returned, to allow further
 * handling by the mediator.
 */
public class SOAPValidator
{

	private static final Log logger = LogFactory.getLog(SOAPValidator.class);

	/**
	 * Validates the SOAP body against the provided XML schema.
	 *
	 * @param schema the compiled XMLValidationSchema for the WSDL/XSD
	 * @param result the SOAP analysis result containing the body
	 * @return a list of XMLValidationProblem, empty if valid
	 * @throws XMLStreamException if an XML parsing error occurs
	 */
	public List<XMLValidationProblem> validate(XMLValidationSchema schema, SOAPAnalysisResult result, Boolean validateHeaders)
			throws XMLStreamException
	{
		logger.debug("Starting SOAP message validation against schema");

		// read & validate headers if enabled
		if(validateHeaders)
		{
			logger.debug("SOAP-Headers validation enabled. Proceeding with validation of headers...");
			for(OMElement header: result.getHeaderElements())
			{
				String sXMLHeader = header.toString();
				logger.debug("SOAP header for validation extracted: " + ((sXMLHeader.length() > 200) ? sXMLHeader.substring(0, 200) : sXMLHeader));
				List<XMLValidationProblem> validationProblems = validate(sXMLHeader, schema);
				if(validationProblems.size() > 0)
				{
					// do not proceed if validator found a violation
					logger.debug("validating header: " + header.getLocalName() + " returned error(s)");
					return validationProblems;
				}
			}
			logger.debug("SOAP Header(s) validation completed. Validation errors: 0");
		}
		
		// Get SOAP body XML as string
		String sXMLPayload = result.getSoapBodyElement().toString();
		logger.debug("SOAP payload extracted: "
				+ (sXMLPayload.length() > 200 ? sXMLPayload.substring(0, 200) + "..." : sXMLPayload));
		List<XMLValidationProblem> validationProblems = validate(sXMLPayload, schema);
		logger.debug("SOAP Body validation completed. Validation errors: " + validationProblems.size());
		
		return validationProblems;
	}
	
	private List<XMLValidationProblem> validate(String inputXMLAsString, XMLValidationSchema schema) throws XMLStreamException
	{
		// Initialize Woodstox input factory
		// To configure factory properties if needed. see:
		// https://github.com/codehaus/woodstox/blob/master/wstx1/src/java/com/ctc/wstx/stax/WstxInputProperties.java
		WstxInputFactory factory = new WstxInputFactory();
		factory.setProperty(XMLInputFactory.IS_COALESCING, true);
		
		// Create XMLStreamReader2 from payload
		XMLStreamReader2 reader = (XMLStreamReader2) factory.createXMLStreamReader(new StringReader(inputXMLAsString));
		
		// Apply schema validation
		reader.validateAgainst(schema);
		
		// Prepare list to collect validation problems
		List<XMLValidationProblem> validationProblems = new ArrayList<>();
		
		// Set handler to capture validation problems
		reader.setValidationProblemHandler(new ValidationProblemHandler()
		{
			@Override
			public void reportProblem(XMLValidationProblem problem) throws XMLValidationException
			{
				validationProblems.add(problem);
				logger.debug("Validation problem detected: " + problem.getMessage());
			}
		});
		
		// Advance the reader to the <Body> element
		while (reader.hasNext())
		{
			int event = reader.next();
			if (event == XMLStreamConstants.START_ELEMENT && "Body".equals(reader.getLocalName()))
			{
				logger.debug("Reached SOAP <Body> element, starting validation from first child");
				break; // Stop before the first child of Body
			}
		}

		// Consume the rest of the XML to trigger validation
		while (reader.hasNext())
		{
			reader.next();
		}
		
		return validationProblems;
	}
}