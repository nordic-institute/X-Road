package ee.ria.xroad.common.message;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;

import org.apache.commons.io.IOUtils;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.util.MimeTypes;

/**
 * Utility class providing helper functionality for SOAP messages.
 */
public final class SoapMessageTestUtil {

    public static final String QUERY_DIR = "../proxy/src/test/queries/";

    private SoapMessageTestUtil() {
    }

    /**
     * Builds SOAP message
     * @param sender sender
     * @param receiver receiver
     * @param userId user id
     * @param queryId query id
     * @return SOAP message
     * @throws Exception in case of any errors
     */
    public static SoapMessageImpl build(ClientId sender,
            ServiceId receiver, String userId, String queryId)
                    throws Exception {
        return build(false, sender, receiver, userId, queryId, null);
    }

    /**
     * Builds SOAP message.
     * @param isRpcEncoded if true, RPC encoded style is used
     * @param sender sender
     * @param receiver receiver
     * @param userId user id
     * @param queryId query id
     * @return SOAP message
     * @throws Exception in case of any errors
     */
    public static SoapMessageImpl build(boolean isRpcEncoded, ClientId sender,
            ServiceId receiver, String userId, String queryId)
                    throws Exception {
        return build(isRpcEncoded, sender, receiver, userId, queryId, null);
    }

    /**
     * Builds SOAP message.
     * @param isRpcEncoded if true, RPC encoded style is used
     * @param sender sender
     * @param receiver receiver
     * @param userId user id
     * @param queryId query id
     * @param createBodyCallback callback to create body of SOAP message
     * @return SOAP message
     * @throws Exception in case of any errors
     */
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

    /**
     * Returns byte array of file.
     * @param fileName file name
     * @return byte array of the file
     * @throws Exception in case of any errors
     */
    public static byte[] fileToBytes(String fileName) throws Exception {
        return IOUtils.toByteArray(newQueryInputStream(fileName));
    }

    /**
     * Gets byte array of SOAP message
     * @param soap SOAP message
     * @return byte array of SOAP message
     * @throws Exception in case of any errors
     */
    public static byte[] messageToBytes(Soap soap) throws Exception {
        if (soap instanceof SoapMessage) {
            return ((SoapMessage)soap).getBytes();
        }

        return soap.getXml().getBytes();
    }

    /**
     * Creates SOAP message from file
     * @param fileName SOAP message file name
     * @return SOAP message
     * @throws Exception in case of any errors
     */
    public static Soap createSoapMessage(String fileName)
            throws Exception {
        return new SoapParserImpl().parse(MimeTypes.TEXT_XML_UTF_8,
                newQueryInputStream(fileName));
    }

    /**
     * Creates SOAP message from byte array.
     * @param data byte array of SOAP message
     * @return SOAP message
     * @throws Exception in case of any errors
     */
    public static Soap createSoapMessage(byte[] data)
            throws Exception {
        return new SoapParserImpl().parse(MimeTypes.TEXT_XML_UTF_8,
                new ByteArrayInputStream(data));
    }

    /**
     * Creates SOAP request message from file
     * @param fileName request file name
     * @return SOAP request
     * @throws Exception in case of any errors
     */
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

    /**
     * Creates SOAP response message from file.
     * @param fileName response file name
     * @return SOAP response
     * @throws Exception in case of any errors
     */
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

    /**
     * Create new query input stream of the query file.
     * @param fileName query file name
     * @return file input stream of the query file.
     * @throws Exception in case of any errors
     */
    public static FileInputStream newQueryInputStream(String fileName)
            throws Exception {
        return new FileInputStream(QUERY_DIR + fileName);
    }
}
