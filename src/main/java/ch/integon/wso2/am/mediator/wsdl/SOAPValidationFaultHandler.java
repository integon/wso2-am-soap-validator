package ch.integon.wso2.am.mediator.wsdl;

import java.util.List;

import javax.xml.namespace.QName;

import org.apache.axiom.soap.*;
import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axis2.AxisFault;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.core.axis2.Axis2Sender;
import org.codehaus.stax2.validation.XMLValidationProblem;

import ch.integon.wso2.am.mediator.wsdl.model.SOAPDirection;

/**
 * Handles SOAP XML validation problems and converts them into proper SOAP
 * faults that can be returned to the client through WSO2 Synapse.
 * <p>
 * Supports both SOAP 1.1 and SOAP 1.2. Determines the direction of the SOAP
 * message (INBOUND, OUTBOUND, FAULT) and sets the appropriate fault code and
 * reason.
 * <p>
 */
public class SOAPValidationFaultHandler
{

	private static final Log logger = LogFactory.getLog(SOAPValidationFaultHandler.class);

	/**
	 * Handles XML validation problems by creating a SOAP fault and sending it back
	 * to the client.
	 * <p>
	 * Determines SOAP version and message direction, sets the fault code and
	 * reason, and sends the fault response.
	 *
	 * @param messageContext     The Synapse message context for the current
	 *                           message.
	 * @param validationProblems List of XMLValidationProblem objects (currently not
	 *                           logged individually).
	 * @param faultMessage       The fault message to include in the SOAP fault.
	 * @return false always, indicating the mediation flow should stop.
	 */
	public boolean handleValidationProblem(MessageContext messageContext, List<XMLValidationProblem> validationProblems,
			String faultMessage)
	{

		// parse fresh because direction could be null (analysis error)
		SOAPDirection direction = messageContext.isFaultResponse() ? SOAPDirection.FAULT
				: messageContext.isResponse() ? SOAPDirection.OUTBOUND : SOAPDirection.INBOUND;

		logger.debug("Handling SOAP validation problem. Direction: " + direction);
		logger.error("SOAP validation failed: " + faultMessage);

		if (validationProblems != null && !validationProblems.isEmpty())
		{
			for (XMLValidationProblem problem : validationProblems)
			{
				logger.error("Validation problem detected: " + problem.getMessage());
			}
		}

		if (messageContext.isSOAP11())
		{
			handleSoap11Fault(messageContext, faultMessage, direction);
		} else
		{
			handleSoap12Fault(messageContext, faultMessage, direction);
		}
		return false;
	}

	/**
	 * Constructs a SOAP 1.1 fault envelope and sends it back to the client.
	 *
	 * @param messageContext The Synapse message context.
	 * @param faultMessage   The fault message to include.
	 * @param direction      Direction of the message (INBOUND/OUTBOUND/FAULT) to
	 *                       determine fault code.
	 */
	private void handleSoap11Fault(MessageContext messageContext, String faultMessage, SOAPDirection direction)
	{
		logger.debug("Building SOAP 1.1 fault envelope for message: " + faultMessage);

		SOAPFactory soap11Factory = OMAbstractFactory.getSOAP11Factory();
		SOAPEnvelope soap11Envelope = soap11Factory.getDefaultFaultEnvelope();
		SOAPFault soap11Fault = soap11Envelope.getBody().getFault();

		SOAPFaultCode soap11FaultCode = soap11Factory.createSOAPFaultCode();
		QName serverQName = new QName(SOAP11Constants.SOAP_ENVELOPE_NAMESPACE_URI,
				direction == SOAPDirection.INBOUND ? "Client" : "Server", "soapenv");
		soap11FaultCode.setText(serverQName);
		soap11Fault.setCode(soap11FaultCode);

		SOAPFaultReason soap11Reason = soap11Factory.createSOAPFaultReason();
		soap11Reason.setText(faultMessage);
		soap11Fault.setReason(soap11Reason);

		sendFaultResponse(messageContext, soap11Envelope);
	}

	/**
	 * Constructs a SOAP 1.2 fault envelope and sends it back to the client.
	 *
	 * @param messageContext The Synapse message context.
	 * @param faultMessage   The fault message to include.
	 * @param direction      Direction of the message (INBOUND/OUTBOUND/FAULT) to
	 *                       determine fault code.
	 */
	private void handleSoap12Fault(MessageContext messageContext, String faultMessage, SOAPDirection direction)
	{
		logger.debug("Building SOAP 1.2 fault envelope for message: " + faultMessage);

		SOAPFactory soap12Factory = OMAbstractFactory.getSOAP12Factory();
		SOAPEnvelope soap12Envelope = soap12Factory.getDefaultFaultEnvelope();
		SOAPFault soap12Fault = soap12Envelope.getBody().getFault();

		SOAPFaultCode soap12FaultCode = soap12Factory.createSOAPFaultCode();
		SOAPFaultValue soap12Value = soap12Factory.createSOAPFaultValue(soap12FaultCode);
		QName receiverQName = new QName(SOAP12Constants.SOAP_ENVELOPE_NAMESPACE_URI,
				direction == SOAPDirection.INBOUND ? "Sender" : "Receiver", "soapenv");
		soap12Value.setText(receiverQName);
		soap12Fault.setCode(soap12FaultCode);

		SOAPFaultReason soap12Reason = soap12Factory.createSOAPFaultReason();
		SOAPFaultText soap12Text = soap12Factory.createSOAPFaultText();
		soap12Text.setText(faultMessage);
		soap12Text.setLang("en");
		soap12Reason.addSOAPText(soap12Text);
		soap12Fault.setReason(soap12Reason);

		sendFaultResponse(messageContext, soap12Envelope);
	}

	/**
	 * Sends the given SOAP envelope back to the client by setting it in the Axis2
	 * message context and invoking Axis2Sender.sendBack().
	 *
	 * @param messageContext The Synapse message context.
	 * @param envelope       The SOAP envelope containing the fault.
	 */
	private void sendFaultResponse(MessageContext messageContext, SOAPEnvelope envelope)
	{
		logger.debug("Sending SOAP fault response back to the client");

		Axis2MessageContext axis2Ctx = (Axis2MessageContext) messageContext;
		org.apache.axis2.context.MessageContext axis2MsgCtx = axis2Ctx.getAxis2MessageContext();

		try
		{
			axis2MsgCtx.setEnvelope(envelope);
		} catch (AxisFault e)
		{
			throw new RuntimeException("Failed to set SOAP fault envelope", e);
		}

		axis2Ctx.setTo(null);
		Axis2Sender.sendBack(messageContext);
	}
}
