package ee.cyber.xroad.mediator.message;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.soap.SOAPMessage;

import static ee.cyber.xroad.mediator.message.XRoadNamespaces.NS_DL_XX;

public class XRoadListMethods extends XRoadMetaServiceImpl {

    private final String service;

    XRoadListMethods(String charset, SOAPMessage soap,
            String service, boolean isRpcEncoded) throws Exception {
        this(getXmlAsString(NS_DL_XX), charset, soap, service, isRpcEncoded);
    }

    XRoadListMethods(String xml, String charset, SOAPMessage soap,
            String service, boolean isRpcEncoded) throws Exception {
        super(xml, charset, isRpcEncoded
                ? new XRoadRpcSoapHeader() : new XRoadDlSoapHeader.XX(), soap);
        this.service = service;
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public String getConsumer() {
        return null;
    }

    @Override
    public String getProducer() {
        return null;
    }

    @Override
    public String getService() {
        return service;
    }

    @Override
    public String getUserId() {
        return null;
    }

    @Override
    public String getQueryId() {
        return null;
    }

    public static InputStream getXmlAsInputStream(String nsUri)
            throws Exception {
        return new ByteArrayInputStream(
                getXmlAsString(nsUri).getBytes(StandardCharsets.UTF_8));
    }

    public static String getXmlAsString(String nsUri) {
        String rpc = XRoadNamespaces.NS_RPC.equals(nsUri)
                ? " SOAP-ENV:encodingStyle=" +
                    "\"http://schemas.xmlsoap.org/soap/encoding/\"" : "";
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<SOAP-ENV:Envelope" + rpc + " xmlns:SOAP-ENV=\"" +
                "http://schemas.xmlsoap.org/soap/envelope/\">" +
                "<SOAP-ENV:Body>" +
                "<xrd:listMethods xmlns:xrd=\"" + nsUri + "\"/>" +
                "</SOAP-ENV:Body>" +
                "</SOAP-ENV:Envelope>";
    }
}
