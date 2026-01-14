package ch.integon.wso2.am.mediator.wsdl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.xml.namespace.QName;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMNode;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axiom.soap.SOAPHeader;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.core.axis2.Axis2MessageContext;

import ch.integon.wso2.am.mediator.wsdl.model.SOAPAnalysisResult;
import ch.integon.wso2.am.mediator.wsdl.model.SOAPDirection;
import ch.integon.wso2.am.mediator.wsdl.model.SOAPValidationException;
import ch.integon.wso2.am.mediator.wsdl.model.SOAPVersion;

/**
 * SOAPAnalyzer is responsible for analyzing a SOAP message contained in a
 * Synapse MessageContext and extracting all information required to validate
 * the payload, such as SOAP version, direction, body element, headers, and
 * SOAPAction.
 */
public class SOAPAnalyzer
{
	private static final Log logger = LogFactory.getLog(SOAPAnalyzer.class);

	// Constants for SOAP 1.1
    private static final String SOAP_NS = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String SOAP_ENVELOPE = "Envelope";
    private static final String SOAP_BODY = "Body";

	/**
	 * Analyzes a SOAP message in the given Synapse MessageContext. Extracts SOAP
	 * direction, version, body element, SOAPAction, and headers. For outbound
	 * messages, only the body element is returned. For fault messages, a
	 * pre-defined fault result is returned.
	 *
	 * @param ctx the Synapse MessageContext containing the SOAP message
	 * @return a SOAPAnalysisResult with all extracted information
	 * @throws SOAPValidationException all custom exceptions
	 */
	public SOAPAnalysisResult analyze(MessageContext ctx) throws SOAPValidationException
	{
		// check direction for parsing-scope optimization
		SOAPDirection soapDirection = readDirection(ctx);
		logger.debug("SOAP direction determined: " + soapDirection);

		if (soapDirection == SOAPDirection.FAULT)
		{
			logger.debug("Message is a SOAP fault, returning fault result");
			return SOAPAnalysisResult.createSOAPAnalysisResultFault();
		}
		
		// get soap action (message context holds soap action header and action from content-type header)
		String soapAction = ctx.getSoapAction();

		// get soap version first to determine required info
		SOAPVersion soapVersion = ctx.isSOAP11() ? SOAPVersion.SOAP_1_1 : SOAPVersion.SOAP_1_2;
		logger.debug("SOAP version determined: " + soapVersion);

		// get the payload / payload related info
		SOAPEnvelope envelope = ((Axis2MessageContext) ctx).getAxis2MessageContext().getEnvelope();
		logger.debug("Received SOAP payload: " + envelope.toString());
		OMElement bodyElement = envelope.getBody().getFirstElement();

		if (bodyElement == null)
		{
			logger.warn("SOAP body is empty");
			throw new SOAPValidationException("soap body empty");
		}

		// check if payload of SOAP1.1 is double-wrapped 
		if (soapVersion == SOAPVersion.SOAP_1_1 && SOAP_ENVELOPE.equals(bodyElement.getLocalName()) && SOAP_NS.equals(bodyElement.getNamespace().getNamespaceURI()))
		{
			// double wrapped - unwrap it
			OMElement innerBody = bodyElement.getFirstChildWithName(new QName(SOAP_NS, SOAP_BODY)).getFirstElement();
			if (innerBody == null)
			{
				logger.warn("SOAP (inner)body is empty");
				throw new SOAPValidationException("soap (inner)body empty");
			}
			bodyElement = innerBody;
			logger.debug("Received SOAP payload was double-wrapped because of no SOAPAction was submitted. Unwrapped payload: " + envelope.toString());
		}

		// check more elements are found than current payload (skip comment and text
		// nodes)
		OMNode sibling = bodyElement.getNextOMSibling();
		while (sibling != null)
		{
			if (sibling.getType() == OMNode.ELEMENT_NODE)
			{
				logger.warn("Found more than one element in the SOAP body, only the first will be analyzed: "
						+ bodyElement.getLocalName());
				break;
			}
			sibling = sibling.getNextOMSibling();
		}

		logger.debug("SOAP body element found: " + bodyElement.getQName());

		// no further analyzing needed for outbound messages
		if (soapDirection == SOAPDirection.OUTBOUND)
		{
			logger.debug("Outbound message, returning result with body only");
			return new SOAPAnalysisResult(soapDirection, null, soapVersion, bodyElement, null);
		}

		// extract headers
		List<OMElement> headerElements = getHeaderElements(ctx);
		logger.debug("Number of SOAP header elements found: " + headerElements.size());
		for (OMElement header : headerElements)
		{
			logger.debug("Header element: " + header.getQName());
		}

		// return result for inbound message
		logger.debug("Returning SOAPAnalysisResult for inbound message");
		return new SOAPAnalysisResult(soapDirection, soapAction, soapVersion, bodyElement, headerElements);
	}

	/**
	 * Determines the SOAP message direction based on the MessageContext.
	 *
	 * @param ctx the Synapse MessageContext
	 * @return SOAPDirection representing INBOUND, OUTBOUND, or FAULT
	 */
	private SOAPDirection readDirection(MessageContext ctx)
	{
		if (ctx.isFaultResponse())
		{
			return SOAPDirection.FAULT;
		} else if (ctx.isResponse())
		{
			return SOAPDirection.OUTBOUND;
		} else
		{
			return SOAPDirection.INBOUND;
		}
	}

	/**
	 * Extracts all SOAP header elements from the given MessageContext.
	 *
	 * @param ctx the Synapse MessageContext containing the SOAP message
	 * @return a List of OMElement representing all SOAP headers; empty if none
	 */
	private List<OMElement> getHeaderElements(MessageContext ctx)
	{
		SOAPEnvelope envelope = ((Axis2MessageContext) ctx).getAxis2MessageContext().getEnvelope();
		SOAPHeader header = envelope.getHeader();

		List<OMElement> headerElements = List.of();

		if (header != null)
		{
			headerElements = new ArrayList<>();
			Iterator<?> iter = header.getChildElements();
			while (iter.hasNext())
			{
				Object obj = iter.next();
				if (obj instanceof OMElement)
				{
					headerElements.add((OMElement) obj);
				}
			}
		}

		return headerElements;
	}
}