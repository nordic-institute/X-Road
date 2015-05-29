package ee.cyber.xroad.mediator.message;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.soap.SOAPMessage;

import static ee.cyber.xroad.mediator.message.V5XRoadNamespaces.NS_DL_XX;

/**
 * ListMethods meta service message.
 */
public class V5XRoadListMethods extends V5XRoadMetaServiceImpl {

    private final String service;

    V5XRoadListMethods(String charset, SOAPMessage soap,
            String service, boolean isRpcEncoded) throws Exception {
        this(getXmlAsString(NS_DL_XX).getBytes(charset), charset, soap,
                service, isRpcEncoded);
    }

    V5XRoadListMethods(byte[] xml, String charset, SOAPMessage soap,
            String service, boolean isRpcEncoded) throws Exception {
        super(xml, charset, isRpcEncoded
                ? new V5XRoadRpcSoapHeader() : new V5XRoadDlSoapHeader.XX(), soap);
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

    /**
     * @param nsUri the namespace
     * @return an input stream containing the request XML with the given namespace
     * @throws Exception in case of any errors
     */
    public static InputStream getXmlAsInputStream(String nsUri)
            throws Exception {
        return new ByteArrayInputStream(
                getXmlAsString(nsUri).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @param nsUri the namespace
     * @return the request XML with the given namespace
     */
    public static String getXmlAsString(String nsUri) {
        String rpc = V5XRoadNamespaces.NS_RPC.equals(nsUri)
                ? " SOAP-ENV:encodingStyle="
                    + "\"http://schemas.xmlsoap.org/soap/encoding/\"" : "";
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                    + "<SOAP-ENV:Envelope" + rpc + " xmlns:SOAP-ENV=\""
                    + "http://schemas.xmlsoap.org/soap/envelope/\">"
                    + "<SOAP-ENV:Body>"
                    + "<xrd:listMethods xmlns:xrd=\"" + nsUri + "\"/>"
                    + "</SOAP-ENV:Body>"
                    + "</SOAP-ENV:Envelope>";
    }
}
