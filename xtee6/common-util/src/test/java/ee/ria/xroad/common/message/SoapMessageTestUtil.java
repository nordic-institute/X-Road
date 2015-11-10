package ee.ria.xroad.common.message;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;

import org.apache.commons.io.IOUtils;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.util.MimeTypes;

public final class SoapMessageTestUtil {

    public static final String QUERY_DIR = "../proxy/src/test/queries/";

    private SoapMessageTestUtil() {
    }

    public static SoapMessageImpl build(ClientId sender,
            ServiceId receiver, String userId, String queryId)
                    throws Exception {
        return build(false, sender, receiver, userId, queryId, null);
    }

    public static SoapMessageImpl build(boolean isRpcEncoded, ClientId sender,
            ServiceId receiver, String userId, String queryId)
                    throws Exception {
        return build(isRpcEncoded, sender, receiver, userId, queryId, null);
    }

    public static SoapMessageImpl build(boolean isRpcEncoded, ClientId sender,
            ServiceId receiver, String userId, String queryId,
            SoapBuilder.SoapBodyCallback createBodyCallback)
                    throws Exception {
        SoapHeader header = new SoapHeader();
        header.setClient(sender);
        header.setService(receiver);
        header.setUserId(userId);
        header.setQueryId(queryId);
        header.setProtocolVersion(new ProtocolVersion());

        SoapBuilder builder = new SoapBuilder();
        builder.setHeader(header);
        builder.setRpcEncoded(isRpcEncoded);
        builder.setCreateBodyCallback(createBodyCallback);

        return builder.build();
    }

    public static byte[] fileToBytes(String fileName) throws Exception {
        return IOUtils.toByteArray(newQueryInputStream(fileName));
    }

    public static byte[] messageToBytes(Soap soap) throws Exception {
        if (soap instanceof SoapMessage) {
            return ((SoapMessage)soap).getBytes();
        }

        return soap.getXml().getBytes();
    }

    public static Soap createSoapMessage(String fileName)
            throws Exception {
        return new SoapParserImpl().parse(MimeTypes.TEXT_XML_UTF_8,
                newQueryInputStream(fileName));
    }

    public static Soap createSoapMessage(byte[] data)
            throws Exception {
        return new SoapParserImpl().parse(MimeTypes.TEXT_XML_UTF_8,
                new ByteArrayInputStream(data));
    }

    public static SoapMessageImpl createRequest(String fileName)
            throws Exception {
        Soap message = createSoapMessage(fileName);
        if (!(message instanceof SoapMessageImpl)) {
            throw new RuntimeException(
                    "Got " + message.getClass() + " instead of SoapMessage");
        }

        if (((SoapMessageImpl) message).isResponse()) {
            throw new RuntimeException("Got response instead of request");
        }

        return (SoapMessageImpl) message;
    }

    public static SoapMessageImpl createResponse(String fileName)
            throws Exception {
        Soap message = createSoapMessage(fileName);
        if (!(message instanceof SoapMessageImpl)) {
            throw new RuntimeException(
                    "Got " + message.getClass() + " instead of SoapResponse");
        }

        if (((SoapMessageImpl) message).isRequest()) {
            throw new RuntimeException("Got request instead of response");
        }

        return (SoapMessageImpl) message;
    }

    public static FileInputStream newQueryInputStream(String fileName)
            throws Exception {
        return new FileInputStream(QUERY_DIR + fileName);
    }
}
