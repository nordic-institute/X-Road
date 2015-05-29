package ee.ria.xroad.common.message;

import javax.xml.soap.SOAPMessage;

import ee.ria.xroad.common.identifier.CentralServiceId;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import static ee.ria.xroad.common.message.SoapUtils.isResponseMessage;
import static ee.ria.xroad.common.message.SoapUtils.isRpcMessage;

/**
 * This class represents the XROAD SOAP message.
 */
public class SoapMessageImpl extends AbstractSoapMessage<SoapHeader> {

    SoapMessageImpl(byte[] rawXml, String charset, SoapHeader header,
            SOAPMessage soap, String serviceName) throws Exception {
        super(rawXml, charset, header, soap, isResponseMessage(serviceName),
                isRpcMessage(soap));
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
     * True if the SOAP message is marked as asynchronous.
     * @return boolean
     */
    public boolean isAsync() {
        return getHeader().isAsync();
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
