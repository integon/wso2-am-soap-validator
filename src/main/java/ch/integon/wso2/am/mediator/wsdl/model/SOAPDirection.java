package ch.integon.wso2.am.mediator.wsdl.model;

/**
 * Represents the direction of a SOAP message in a mediation flow.
 * <p>
 * <ul>
 *   <li>{@link #INBOUND} - The message is incoming to the system.</li>
 *   <li>{@link #OUTBOUND} - The message is outgoing from the system.</li>
 *   <li>{@link #FAULT} - The message represents a SOAP fault.</li>
 * </ul>
 * </p>
 * <p>
 * Used by mediators and validators to determine the processing logic
 * based on the message direction.
 * </p>
 */
public enum SOAPDirection {
    INBOUND,
    OUTBOUND,
    FAULT
}