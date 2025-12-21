package ch.integon.wso2.am.mediator.wsdl.model;

import java.util.List;
import org.apache.axiom.om.OMElement;

/**
 * Represents the analyzed details of a SOAP message.
 * <p>
 * This immutable class encapsulates key information extracted from a SOAP
 * envelope, including the SOAP action, version, direction (INBOUND, OUTBOUND,
 * FAULT), the SOAP body element, and any header elements.
 * </p>
 */
public final class SOAPAnalysisResult
{

	private final String soapAction;
	private final SOAPVersion soapVersion;

	private final SOAPDirection soapDirection;

	private final OMElement soapBodyElement;
	private final List<OMElement> headerElements;

	public SOAPAnalysisResult(SOAPDirection soapDirection, String soapAction, SOAPVersion soapVersion,
			OMElement soapBodyElement, List<OMElement> headerElements)
	{
		this.soapDirection = soapDirection;
		this.soapAction = soapAction;
		this.soapBodyElement = soapBodyElement;
		this.soapVersion = soapVersion;
		this.headerElements = headerElements == null ? List.of() : List.copyOf(headerElements);
	}

	public SOAPVersion getSoapVersion()
	{
		return soapVersion;
	}

	public SOAPDirection getSoapDirection()
	{
		return soapDirection;
	}
	
	public String getSoapAction()
	{
		return soapAction;
	}

	public OMElement getSoapBodyElement()
	{
		return soapBodyElement;
	}

	public List<OMElement> getHeaderElements()
	{
		return headerElements;
	}

	// enum equal helpers
	public boolean isInbound()
	{
		return soapDirection == SOAPDirection.INBOUND;
	}

	public boolean isOutbound()
	{
		return soapDirection == SOAPDirection.OUTBOUND;
	}

	public boolean isFault()
	{
		return soapDirection == SOAPDirection.FAULT;
	}

	public static SOAPAnalysisResult createSOAPAnalysisResultFault()
	{
		return new SOAPAnalysisResult(SOAPDirection.FAULT, null, null, null, null);
	}
}
