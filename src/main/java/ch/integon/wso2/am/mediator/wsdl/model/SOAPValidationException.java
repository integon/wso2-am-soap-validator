package ch.integon.wso2.am.mediator.wsdl.model;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Exception thrown when a SOAP payload fails validation against its XML schema.
 * <p>
 * Can be used to wrap other exceptions encountered during schema compilation or
 * SOAP message validation.
 */
public class SOAPValidationException extends Exception
{
	private static final long serialVersionUID = 1L;

	private static final Log logger = LogFactory.getLog(SOAPValidationException.class);

	/**
	 * Constructs a SOAPValidationException with the specified detail message.
	 * 
	 * @param message The detail message explaining the validation failure.
	 */
	public SOAPValidationException(String message)
	{
		super(message);
		if (logger.isDebugEnabled())
		{
			logger.debug("SOAPValidationException created with message: " + message);
		}
	}

	/**
	 * Constructs a SOAPValidationException with the specified detail message and
	 * cause.
	 * 
	 * @param message The detail message explaining the validation failure.
	 * @param cause   The underlying cause of the exception.
	 */
	public SOAPValidationException(String message, Throwable cause)
	{
		super(message, cause);
		if (logger.isDebugEnabled())
		{
			logger.debug("SOAPValidationException created with message: " + message, cause);
		}
	}
}
