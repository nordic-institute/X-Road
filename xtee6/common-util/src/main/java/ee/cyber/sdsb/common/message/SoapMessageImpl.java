package ee.cyber.sdsb.common.message;

import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;

import ee.cyber.sdsb.common.identifier.AbstractServiceId;
import ee.cyber.sdsb.common.identifier.ClientId;

public class SoapMessageImpl implements SoapMessage {

    // HTTP header field for async messages that are sent from async-sender
    public static final String X_IGNORE_ASYNC = "X-Ignore-Async";

    public static final String NS_SDSB = "http://sdsb.net/xsd/sdsb.xsd";
    public static final String PREFIX_SDSB = "sdsb";

    final SOAPMessage soap;

    private final String xml;

    protected final SoapHeader header;

    protected final boolean isResponse;
    protected final boolean isRpcEncoded;

    SoapMessageImpl(String xml, SOAPMessage soap, SoapHeader header,
            String serviceName) throws Exception {
        this.soap = soap;
        this.header = header;
        this.xml = xml;
        this.isResponse = SoapUtils.isResponseMessage(serviceName);
        this.isRpcEncoded = SoapUtils.isRpcMessage(soap);
    }

    @Override
    public String getXml() {
        return xml;
    }

    @Override
    public SOAPBody getBody() throws SOAPException {
        return soap.getSOAPBody();
    }

    @Override
    public String getCharset() {
        return header.charset;
    }

    @Override
    public boolean isRpcEncoded() {
        return isRpcEncoded;
    }

    public ClientId getClient() {
        return header.client;
    }

    public AbstractServiceId getService() {
        return header.service;
    }

    public boolean isAsync() {
        return header.isAsync;
    }

    public String getQueryId() {
        return header.queryId;
    }

    public String getUserId() {
        return header.userId;
    }

    @Override
    public boolean isRequest() {
        return !isResponse;
    }

    @Override
    public boolean isResponse() {
        return isResponse;
    }

    public byte[] getBytes() throws Exception {
        return getXml().getBytes(getCharset());
    }
}
