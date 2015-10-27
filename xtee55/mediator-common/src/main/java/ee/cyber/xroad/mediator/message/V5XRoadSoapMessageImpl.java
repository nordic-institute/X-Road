package ee.cyber.xroad.mediator.message;

import javax.xml.soap.SOAPMessage;

import ee.ria.xroad.common.message.AbstractSoapMessage;

import static ee.ria.xroad.common.message.SoapUtils.isResponseMessage;

/**
 * Implementation of X-Road 5.0 SOAP message.
 */
public class V5XRoadSoapMessageImpl extends AbstractSoapMessage<V5XRoadSoapHeader> {

    private String serviceName;
    private String serviceVersion;

    V5XRoadSoapMessageImpl(byte[] xml, String charset, V5XRoadSoapHeader header,
            SOAPMessage soap, String serviceName, String originalContentType)
                    throws Exception {
        super(xml, charset, header, soap, isResponseMessage(serviceName),
                header instanceof V5XRoadRpcSoapHeader, originalContentType);

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

    /**
     * @return true if the message is asynchronous
     */
    public boolean isAsync() {
        return getHeader().isAsync();
    }

    /**
     * @return the consumer short name
     */
    public String getConsumer() {
        return getHeader().getConsumer();
    }

    /**
     * @return the producer short name
     */
    public String getProducer() {
        return getHeader().getProducer();
    }

    /**
     * @return the service ID
     */
    public String getService() {
        return getHeader().getService();
    }

    /**
     * @return the user ID
     */
    public String getUserId() {
        return getHeader().getUserId();
    }

    /**
     * @return the query ID
     */
    public String getQueryId() {
        return getHeader().getQueryId();
    }

    /**
     * @return the service name
     */
    public String getServiceName() {
        return serviceName;
    }

    /**
     * @return the service version
     */
    public String getServiceVersion() {
        return serviceVersion;
    }
}
