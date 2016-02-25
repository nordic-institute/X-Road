package ee.ria.xroad.common.message;

import javax.xml.soap.SOAPMessage;

import ee.ria.xroad.common.identifier.CentralServiceId;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import static ee.ria.xroad.common.message.SoapUtils.isResponseMessage;
import static ee.ria.xroad.common.message.SoapUtils.isRpcMessage;

/**
 * This class represents the X-Road SOAP message.
 */
public class SoapMessageImpl extends AbstractSoapMessage<SoapHeader> {

    SoapMessageImpl(byte[] rawXml, String charset, SoapHeader header,
            SOAPMessage soap, String serviceName,
            String originalContentType) throws Exception {
        super(rawXml, charset, header, soap, isResponseMessage(serviceName),
                isRpcMessage(soap), originalContentType);
    }

    /**
     * Gets the client ID in the SOAP message header.
     * @return ClientId
     */
    public ClientId getClient() {
        return getHeader().getClient();
    }

    /**
     * Gets the service ID in the SOAP message header.
     * @return ServiceId
     */
    public ServiceId getService() {
        return getHeader().getService();
    }

    /**
     * Gets the central service ID in the SOAP message header.
     * @return CentralServiceId
     */
    public CentralServiceId getCentralService() {
        return getHeader().getCentralService();
    }

    /**
     * Gets the query ID from the SOAP message header.
     * @return String
     */
    public String getQueryId() {
        return getHeader().getQueryId();
    }

    /**
     * Gets the user ID from the SOAP message header.
     * @return String
     */
    public String getUserId() {
        return getHeader().getUserId();
    }
}
