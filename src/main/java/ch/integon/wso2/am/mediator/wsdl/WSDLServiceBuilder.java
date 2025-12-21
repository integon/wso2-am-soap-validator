package ch.integon.wso2.am.mediator.wsdl;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.wsdl.WSDLException;
import javax.xml.namespace.QName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.Bus;
import org.apache.cxf.BusException;
import org.apache.cxf.binding.soap.model.SoapOperationInfo;
import org.apache.cxf.bus.CXFBusFactory;
import org.apache.cxf.service.model.BindingFaultInfo;
import org.apache.cxf.service.model.BindingInfo;
import org.apache.cxf.service.model.BindingOperationInfo;
import org.apache.cxf.service.model.MessagePartInfo;
import org.apache.cxf.service.model.ServiceInfo;
import org.apache.cxf.wsdl.WSDLManager;
import org.apache.cxf.wsdl11.WSDLManagerImpl;

import ch.integon.wso2.am.mediator.wsdl.model.SOAPServiceOperation;

/**
 * WSDLServiceBuilder is responsible for building CXF ServiceInfo objects from
 * WSDL definitions and for matching SOAP operations based on SOAP action or
 * body QName.
 * <p>
 * It initializes a CXF Bus and WSDLManager to parse WSDL files, then provides
 * utility methods to find the correct service and operation for a SOAP request.
 * </p>
 */
public class WSDLServiceBuilder
{
	private static final Log logger = LogFactory.getLog(WSDLServiceBuilder.class);

	/**
	 * Builds a list of ServiceInfo objects from the given WSDL URIs.
	 * 
	 * @param wsdlURIs array of URIs pointing to WSDL files
	 * @return list of ServiceInfo objects extracted from the WSDLs
	 */
	public List<ServiceInfo> buildServices(URI[] wsdlURIs)
	{
		logger.debug("Initializing CXF bus and WSDL manager");
		Bus bus = CXFBusFactory.newInstance().createBus();
		WSDLManager wsdlManager = bus.getExtension(WSDLManager.class);
		if (wsdlManager == null)
		{
			try
			{
				wsdlManager = new WSDLManagerImpl();
			} catch (BusException e)
			{
				logger.error(e);
				return null;
			}
			bus.setExtension(wsdlManager, WSDLManager.class);
			logger.debug("Created new WSDLManagerImpl");
		}

		org.apache.cxf.wsdl11.WSDLServiceBuilder wsdlBuilder = new org.apache.cxf.wsdl11.WSDLServiceBuilder(bus);

		// Load WSDL definition
		logger.debug("Loading WSDL definition from URIs: " + wsdlURIs);
		List<ServiceInfo> services = new ArrayList<ServiceInfo>();
		for (URI uri : wsdlURIs)
		{
			try
			{
				services.addAll(wsdlBuilder.buildServices(wsdlManager.getDefinition(uri.toString())));
			} catch (WSDLException e)
			{
				logger.error(e);
				return null;
			}
		}

		return services;
	}

	/**
	 * Finds a matching SOAP service and operation based on the provided SOAP
	 * action or body QName.
	 * <p>
	 * Matches are attempted in the following order: SOAP action, input body QName,
	 * output body QName, and fault parts.
	 * </p>
	 * 
	 * @param services   list of ServiceInfo objects to search
	 * @param soapAction SOAP action header value (may be null)
	 * @param bodyQName  QName of the SOAP body element
	 * @return SOAPServiceOperation containing the matched service and operation,
	 *         or null if no match found
	 */
	public SOAPServiceOperation getMatchedServiceOperation(List<ServiceInfo> services, String soapAction, QName bodyQName)
	{
    	logger.debug("finding matching service based on (soap) action: " + ((soapAction == null) ? "<not-defined>" : soapAction) + " and bodyQName: " + ((bodyQName == null) ? "<not-defined>" : bodyQName.toString()));
    	ServiceInfo matchedServiceByAction = null;
    	BindingOperationInfo matchedOperationByAction = null;
    	
    	ServiceInfo matchedServiceByBodyName = null;
    	BindingOperationInfo matchedOperationByBodyName = null;
    	
    	logger.debug("looping through services...");
    	for (ServiceInfo service : services) 
    	{
    		logger.debug("service: " + service.getName());
            for (BindingInfo binding : service.getBindings()) 
            {
            	logger.debug("  binding: " + binding.getName());
                for (BindingOperationInfo operation : binding.getOperations()) 
                {
                	logger.debug("    operation: " + operation.getName());
                	// find match by soap action
                	if(soapAction != null && !"".equals(soapAction))
                	{
                		logger.debug("       checking by soapAction: " + soapAction);
                		
	                	SoapOperationInfo soapOperation = operation.getExtensor(SoapOperationInfo.class);
	                	logger.debug("        soapOperation action (maybe empty): " + soapOperation.getAction());
	                	if (soapOperation != null && soapAction.equals(soapOperation.getAction()))
						{
	                		logger.debug("        matching service and operation found by (soap) action! service: " + service.getName() + " operation: " + operation.getName());
	                		matchedServiceByAction = service;
	                		matchedOperationByAction = operation;
						}
                	}
                	
                	// find match by bodyQName INBOUND
                	if(operation.getInput() != null)
                	{
	                	for (MessagePartInfo part : operation.getInput().getMessageParts()) 
	                    {
	                		logger.debug("        operation inbound part: " + part.getName());
	                		if(bodyQName.equals(part.getElementQName()))
	                		{
	                			logger.debug("        matching service and operation found by bodyQName in input parts! service: " + service.getName() + " operation: " + operation.getName());
	                			matchedServiceByBodyName = service;
	                			matchedOperationByBodyName = operation;
	                		}
	                    }
                	}
                	
                	// find match by bodyQName OUTBOUND
                	if(operation.getOutput() != null)
                	{
	                	for (MessagePartInfo part : operation.getOutput().getMessageParts()) 
	                    {
	                		logger.debug("        operation outbound part: " + part.getName());
	                		if(bodyQName.equals(part.getElementQName()))
	                		{
	                			logger.debug("        matching service and operation found by bodyQName in output parts! service: " + service.getName() + " operation: " + operation.getName());
	                			matchedServiceByBodyName = service;
	                			matchedOperationByBodyName = operation;
	                		}
	                    }
                	}
                	
                	// find match by bodyQName FAULT
                	if(operation.getFaults() != null)
                	{
	                	for (BindingFaultInfo bindingFaultInfo : operation.getFaults()) {
	                		logger.debug("        operation fault info name: " + bindingFaultInfo.getFaultInfo().getFaultName());
	                        for (MessagePartInfo part : bindingFaultInfo.getFaultInfo().getMessageParts())
	                        {
	                        	logger.debug("          operation fault part: " + part.getName());
	                        	if(bodyQName.equals(part.getElementQName()))
	                    		{
	                        		logger.debug("          matching service and operation found by bodyQName in fault parts! service: " + service.getName() + " operation: " + operation.getName());
	                    			matchedServiceByBodyName = service;
	                    			matchedOperationByBodyName = operation;
	                    		}
	                        }
	                	}
                	}
                }
            }
    	}
    	
    	if (matchedServiceByAction == null && matchedServiceByBodyName == null)
    	{
    		logger.debug("No matching service found by SOAP action or body QName");
    		return null;
    	}
    	
    	if (matchedServiceByAction != null && matchedServiceByBodyName != null)
    	{
    		if(matchedServiceByAction != matchedServiceByBodyName)
    		{
    			throw new IllegalStateException("service mismatch: (soap) action does not match body");
    		}
    		
    		if(matchedOperationByAction != matchedOperationByBodyName)
    		{
    			throw new IllegalStateException("operation mismatch: (soap) action does not match body");
    		}
    	}
    	
    	ServiceInfo returnServiceInfo = matchedServiceByAction == null ? matchedServiceByBodyName : matchedServiceByAction;
    	BindingOperationInfo returnOperationInfo = matchedOperationByAction == null ? matchedOperationByBodyName : matchedOperationByBodyName;
    	logger.debug("Returning matched SOAPServiceOperation with service: " + returnServiceInfo + " and operation: " + returnOperationInfo);
    	
    	return new SOAPServiceOperation(returnServiceInfo, returnOperationInfo);
	}
}
