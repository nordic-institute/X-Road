package ee.ria.xroad.common.identifier;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Unmarshaller;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.message.JaxbUtils;

import static ee.ria.xroad.common.ErrorCodes.X_INVALID_XML;

/**
 * XML node parser for X-Road identifiers.
 */
public final class IdentifierXmlNodeParser {

    public static final String NS_IDENTIFIERS =
            "http://x-road.eu/xsd/identifiers";

    public static final String PREFIX_IDENTIFIERS = "id";

    private static final JAXBContext JAXB_CTX =
            JaxbUtils.initJAXBContext(ObjectFactory.class);

    private IdentifierXmlNodeParser() {
    }

    /**
     * Parses a client ID from the given node.
     * @param node the node from which to parse the client ID
     * @return ClientId
     * @throws Exception if errors occur during parsing
     */
    public static ClientId parseClientId(Node node) throws Exception {
        XRoadClientIdentifierType type =
                parseType(XRoadObjectType.MEMBER, node,
                        XRoadClientIdentifierType.class);
        return IdentifierTypeConverter.parseClientId(type);
    }

    /**
     * Parses a service ID from the given node.
     * @param node the node from which to parse the service ID
     * @return ServiceId
     * @throws Exception if errors occur during parsing
     */
    public static ServiceId parseServiceId(Node node) throws Exception {
        XRoadObjectType objectType = getObjectType(node);
        if (objectType.equals(XRoadObjectType.CENTRALSERVICE)) {
            XRoadCentralServiceIdentifierType type =
                    parseType(XRoadObjectType.CENTRALSERVICE, node,
                            XRoadCentralServiceIdentifierType.class);
            return IdentifierTypeConverter.parseCentralServiceId(type);
        } else {
            XRoadServiceIdentifierType type =
                    parseType(XRoadObjectType.SERVICE, node,
                            XRoadServiceIdentifierType.class);
            return IdentifierTypeConverter.parseServiceId(type);
        }
    }

    // -- Helper methods ------------------------------------------------------

    static <T> T parseType(XRoadObjectType expectedType, Node node,
            Class<T> clazz) throws Exception {
        verifyObjectType(node, expectedType);

        Unmarshaller unmarshaller = JAXB_CTX.createUnmarshaller();
        JAXBElement<T> element = unmarshaller.unmarshal(node, clazz);
        return element.getValue();
    }

    static void verifyObjectType(Node node, XRoadObjectType expected)
            throws Exception {
        XRoadObjectType type = getObjectType(node);
        if (!expected.equals(type)) {
            throw new CodedException(X_INVALID_XML,
                    "Unexpected objectType: %s", type);
        }
    }

    static XRoadObjectType getObjectType(Node node) throws Exception {
        Node objectType = null;

        NamedNodeMap attr = node.getAttributes();
        if (attr != null) {
            objectType = attr.getNamedItemNS(NS_IDENTIFIERS, "objectType");
        }

        if (objectType == null) {
            throw new CodedException(X_INVALID_XML,
                    "Missing objectType attribute");
        }

        String typeName = objectType.getTextContent();
        if (typeName == null) {
            throw new CodedException(X_INVALID_XML,
                    "ObjectType not specified");
        }

        try {
            return XRoadObjectType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            throw new CodedException(X_INVALID_XML,
                    "Unknown objectType: %s", typeName);
        }
    }
}
