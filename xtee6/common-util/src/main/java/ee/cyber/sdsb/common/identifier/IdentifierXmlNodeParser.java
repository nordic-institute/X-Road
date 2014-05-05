package ee.cyber.sdsb.common.identifier;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Unmarshaller;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import ee.cyber.sdsb.common.CodedException;

import static ee.cyber.sdsb.common.ErrorCodes.X_INVALID_XML;

public class IdentifierXmlNodeParser {

    public static final String NS_IDENTIFIERS =
            "http://sdsb.net/xsd/identifiers";

    public static final String PREFIX_IDENTIFIERS = "id";

    public static ClientId parseClientId(Node node) throws Exception {
        SdsbClientIdentifierType type =
                parseType(SdsbObjectType.MEMBER, node,
                        SdsbClientIdentifierType.class);
        return IdentifierTypeConverter.parseClientId(type);
    }

    public static AbstractServiceId parseServiceId(Node node) throws Exception {
        SdsbObjectType objectType = getObjectType(node);
        if (objectType.equals(SdsbObjectType.CENTRALSERVICE)) {
            SdsbCentralServiceIdentifierType type =
                    parseType(SdsbObjectType.CENTRALSERVICE, node,
                            SdsbCentralServiceIdentifierType.class);
            return IdentifierTypeConverter.parseCentralServiceId(type);
        } else {
            SdsbServiceIdentifierType type =
                    parseType(SdsbObjectType.SERVICE, node,
                            SdsbServiceIdentifierType.class);
            return IdentifierTypeConverter.parseServiceId(type);
        }
    }

    // -- Helper methods ------------------------------------------------------

    static <T> T parseType(SdsbObjectType expectedType, Node node,
            Class<T> clazz) throws Exception {
        verifyObjectType(node, expectedType);

        JAXBContext ctx = JAXBContext.newInstance(ObjectFactory.class);
        Unmarshaller unmarshaller = ctx.createUnmarshaller();
        JAXBElement<T> element = unmarshaller.unmarshal(node, clazz);

        return element.getValue();
    }

    static void verifyObjectType(Node node, SdsbObjectType expected)
            throws Exception {
        SdsbObjectType type = getObjectType(node);
        if (!expected.equals(type)) {
            throw new CodedException(X_INVALID_XML,
                    "Unexpected objectType: %s", type);
        }
    }

    static SdsbObjectType getObjectType(Node node) throws Exception {
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
            return SdsbObjectType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            throw new CodedException(X_INVALID_XML,
                    "Unknown objectType: %s", typeName);
        }
    }
}
