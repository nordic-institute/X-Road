package ee.ria.xroad_legacy.common.message;

import javax.xml.soap.SOAPMessage;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import static ee.ria.xroad_legacy.common.message.SoapUtils.isResponseMessage;
import static ee.ria.xroad_legacy.common.message.SoapUtils.isRpcMessage;

/**
 * This class represents the XROAD SOAP message.
 */
public class SoapMessageImpl extends AbstractSoapMessage<SoapHeader> {

    SoapMessageImpl(String xml, String charset, SoapHeader header,
            SOAPMessage soap, String serviceName) throws Exception {
        super(xml, charset, header, soap, isResponseMessage(serviceName),
                isRpcMessage(soap));
    }

    public ClientId getClient() {
        return getHeader().getClient();
    }

    public ServiceId getService() {
        return getHeader().getService();
    }

    public boolean isAsync() {
        return getHeader().isAsync();
    }

    public String getQueryId() {
        return getHeader().getQueryId();
    }

    public String getUserId() {
        return getHeader().getUserId();
    }

    public byte[] getBytes() throws Exception {
        return getXml().getBytes(getCharset());
    }
}
