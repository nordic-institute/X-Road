package ee.cyber.sdsb.common.request;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;

import ee.cyber.sdsb.common.message.SoapMessageImpl;

import static ee.cyber.sdsb.common.request.ManagementRequests.*;

/**
 * Parser for management requests.
 */
public class ManagementRequestParser {

    private static final Logger LOG =
            LoggerFactory.getLogger(ManagementRequestParser.class);

    private static final JAXBContext jaxbCtx = initJaxbContext();

    // -- Public API methods --------------------------------------------------

    public static AuthCertRegRequestType parseAuthCertRegRequest(
            SoapMessageImpl message) throws Exception {
        return parse(message, AUTH_CERT_REG);
    }

    public static AuthCertDeletionRequestType parseAuthCertDeletionRequest(
            SoapMessageImpl message) throws Exception {
        return parse(message, AUTH_CERT_DELETION);
    }

    public static ClientRequestType parseClientRegRequest(SoapMessageImpl message)
            throws Exception {
        return parse(message, CLIENT_REG);
    }

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
            LOG.error("Content node name ({}) does not match " +
                    "expected name ({})", nodeName, expectedNodeName);
            throw new RuntimeException("Unexpected content: " + nodeName);
        }

        Unmarshaller um = jaxbCtx.createUnmarshaller();
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
