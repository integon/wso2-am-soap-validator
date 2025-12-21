package ch.integon.wso2.am.mediator.wsdl.model;

import org.apache.cxf.service.model.BindingOperationInfo;
import org.apache.cxf.service.model.ServiceInfo;

/**
 * Represents a pairing of a CXF {@link ServiceInfo} and its corresponding
 * {@link BindingOperationInfo} within a WSDL.
 * <p>
 * This class is used to identify a specific SOAP service operation for
 * validation, schema resolution, or runtime invocation.
 * </p>
 */
public class SOAPServiceOperation
{
	private ServiceInfo service;
	private BindingOperationInfo operation;
	
	public SOAPServiceOperation(ServiceInfo service, BindingOperationInfo operation)
	{
		super();
		this.service = service;
		this.operation = operation;
	}

	public ServiceInfo getService()
	{
		return service;
	}

	public void setService(ServiceInfo service)
	{
		this.service = service;
	}

	public BindingOperationInfo getOperation()
	{
		return operation;
	}

	public void setOperation(BindingOperationInfo operation)
	{
		this.operation = operation;
	}
	
}
