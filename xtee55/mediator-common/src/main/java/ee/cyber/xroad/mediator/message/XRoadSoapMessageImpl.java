package ee.cyber.xroad.mediator.message;

import javax.xml.soap.SOAPMessage;

import ee.cyber.sdsb.common.message.AbstractSoapMessage;

import static ee.cyber.sdsb.common.message.SoapUtils.isResponseMessage;

/**
 * Implementation of X-Road 5.0 SOAP message.
 */
public class XRoadSoapMessageImpl extends AbstractSoapMessage<XRoadSoapHeader> {

    private String serviceName;
    private String serviceVersion;

    XRoadSoapMessageImpl(byte[] xml, String charset, XRoadSoapHeader header,
            SOAPMessage soap, String serviceName) throws Exception {
        super(xml, charset, header, soap, isResponseMessage(serviceName),
                header instanceof XRoadRpcSoapHeader);

        parseServiceNameAndVersion(header.getService());
    }

    private void parseServiceNameAndVersion(String fullServiceName) {
        if (fullServiceName != null) {
            String[] parts = fullServiceName.split("\\.");
            boolean hasVersion = parts[parts.length - 1].matches("^v[\\d]+$");
            this.serviceVersion = hasVersion ? parts[parts.length - 1] : null;
            this.serviceName = hasVersion
                    ? parts[parts.length - 2] : parts[parts.length - 1];
        }
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
}
