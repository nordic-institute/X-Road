package ee.cyber.xroad.mediator.message;

import javax.xml.soap.SOAPMessage;

import ee.cyber.sdsb.common.message.AbstractSoapMessage;

import static ee.cyber.sdsb.common.message.SoapUtils.isResponseMessage;

/**
 * Implementation of X-Road 5.0 SOAP message.
 */
public class XRoadSoapMessageImpl extends AbstractSoapMessage<XRoadSoapHeader> {

    private final String serviceName;
    private final String serviceVersion;

    XRoadSoapMessageImpl(String xml, String charset, XRoadSoapHeader header,
            SOAPMessage soap, String serviceName) throws Exception {
        super(xml, charset, header, soap, isResponseMessage(serviceName),
                header instanceof XRoadRpcSoapHeader);

        this.serviceName =
                getPartFromFullServiceName(header.getService(), 1);
        this.serviceVersion =
                getPartFromFullServiceName(header.getService(), 2);
    }

    public boolean isAsync() {
        return getHeader().isAsync();
    }

    public String getConsumer() {
        return getHeader().getConsumer();
    }

    public String getProducer() {
        return getHeader().getProducer();
    }

    public String getService() {
        return getHeader().getService();
    }

    public String getUserId() {
        return getHeader().getUserId();
    }

    public String getQueryId() {
        return getHeader().getQueryId();
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getServiceVersion() {
        return serviceVersion;
    }

    private static final String getPartFromFullServiceName(
            String fullServiceName, int partIndex) {
        if (fullServiceName != null) {
            String[] parts = fullServiceName.split("\\.");
            if (parts.length >= partIndex + 1) {
                return parts[partIndex];
            }
        }

        return null;
    }
}
