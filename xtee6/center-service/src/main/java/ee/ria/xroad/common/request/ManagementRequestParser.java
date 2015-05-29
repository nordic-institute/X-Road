package ee.ria.xroad.common.request;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;

import ee.ria.xroad.common.message.SoapMessageImpl;

import static ee.ria.xroad.common.request.ManagementRequests.*;

/**
 * Parser for management requests.
 */
public final class ManagementRequestParser {

    private static final Logger LOG =
            LoggerFactory.getLogger(ManagementRequestParser.class);

    private static final JAXBContext JAXB_CTX = initJaxbContext();

    private ManagementRequestParser() {
    }

    // -- Public API methods --------------------------------------------------

    /**
     * Parses an authentication certificate registration request.
     * @param message the request SOAP message
     * @return the authentication certificate registration request
     * @throws Exception in case of any errors
     */
    public static AuthCertRegRequestType parseAuthCertRegRequest(
            SoapMessageImpl message) throws Exception {
        return parse(message, AUTH_CERT_REG);
    }

    /**
     * Parses an authentication certificate deletion request.
     * @param message the request SOAP message
     * @return the authentication certificate deletion request
     * @throws Exception in case of any errors
     */
    public static AuthCertDeletionRequestType parseAuthCertDeletionRequest(
            SoapMessageImpl message) throws Exception {
        return parse(message, AUTH_CERT_DELETION);
    }

    /**
     * Parses a client registration request.
     * @param message the request SOAP message
     * @return the client registration request
     * @throws Exception in case of any errors
     */
    public static ClientRequestType parseClientRegRequest(SoapMessageImpl message)
            throws Exception {
        return parse(message, CLIENT_REG);
    }

    /**
     * Parses a client deletion request.
     * @param message the request SOAP message
     * @return the client rdeletion request
     * @throws Exception in case of any errors
     */
    public static ClientRequestType parseClientDeletionRequest(
            SoapMessageImpl message) throws Exception {
        return parse(message, CLIENT_DELETION);
    }

    // -- Private helper methods ----------------------------------------------

    private static <T> T parse(SoapMessageImpl message, String expectedNodeName)
            throws Exception {
        LOG.debug("parse(expectedNodeName: {}, message: {})", expectedNodeName,
                message.getXml());

        Node node = message.getSoap().getSOAPBody().getFirstChild();
        if (node == null) {
            LOG.error("Message is missing content node");
            throw new RuntimeException("SoapMessage has no content");
        }

        String nodeName = node.getLocalName();
        if (!expectedNodeName.equalsIgnoreCase(nodeName)) {
            LOG.error("Content node name ({}) does not match "
                    + "expected name ({})", nodeName, expectedNodeName);
            throw new RuntimeException("Unexpected content: " + nodeName);
        }

        Unmarshaller um = JAXB_CTX.createUnmarshaller();
        try {
            @SuppressWarnings("unchecked")
            JAXBElement<T> req = (JAXBElement<T>) um.unmarshal(node);
            return req.getValue();
        } catch (JAXBException e) {
            String m = String.format("Failed to parse '%s'", expectedNodeName);

            LOG.error(m, e);
            throw new RuntimeException(m, e);
        }
    }

    private static JAXBContext initJaxbContext() {
        try {
            return JAXBContext.newInstance(ObjectFactory.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
