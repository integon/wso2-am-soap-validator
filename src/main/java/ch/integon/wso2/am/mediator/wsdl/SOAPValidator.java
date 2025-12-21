package ch.integon.wso2.am.mediator.wsdl;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

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
	public List<XMLValidationProblem> validate(XMLValidationSchema schema, SOAPAnalysisResult result)
			throws XMLStreamException
	{
		logger.debug("Starting SOAP payload validation");

		// Initialize Woodstox input factory
		WstxInputFactory factory = new WstxInputFactory();
		factory.setProperty(XMLInputFactory.IS_COALESCING, true);
		// TODO: Configure factory properties if needed. see:
		// https://github.com/codehaus/woodstox/blob/master/wstx1/src/java/com/ctc/wstx/stax/WstxInputProperties.java

		// Get SOAP body XML as string
		String xmlPayload = result.getSoapBodyElement().toString();
		logger.debug("SOAP payload extracted: "
				+ (xmlPayload.length() > 200 ? xmlPayload.substring(0, 200) + "..." : xmlPayload));

		// Create XMLStreamReader2 from payload
		XMLStreamReader2 reader = (XMLStreamReader2) factory.createXMLStreamReader(new StringReader(xmlPayload));

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

		logger.debug("SOAP validation completed. Number of problems found: " + validationProblems.size());
		return validationProblems;
	}
}