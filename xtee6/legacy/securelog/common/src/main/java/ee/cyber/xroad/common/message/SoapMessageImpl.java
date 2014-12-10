package ee.cyber.xroad.common.message;

import javax.xml.soap.SOAPMessage;

import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.ServiceId;

import static ee.cyber.xroad.common.message.SoapUtils.isResponseMessage;
import static ee.cyber.xroad.common.message.SoapUtils.isRpcMessage;

/**
 * This class represents the SDSB SOAP message.
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
