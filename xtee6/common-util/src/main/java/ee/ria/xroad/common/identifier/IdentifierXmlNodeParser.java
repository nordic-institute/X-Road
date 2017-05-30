/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.common.identifier;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Unmarshaller;

import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.message.JaxbUtils;

import static ee.ria.xroad.common.ErrorCodes.X_INVALID_XML;

/**
 * XML node parser for X-Road identifiers.
 */
@Slf4j
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
        XroadClientIdentifierType type =
                parseType(XroadObjectType.MEMBER, node,
                        XroadClientIdentifierType.class);
        return IdentifierTypeConverter.parseClientId(type);
    }

    /**
     * Parses a service ID from the given node.
     * @param node the node from which to parse the service ID
     * @return ServiceId
     * @throws Exception if errors occur during parsing
     */
    public static ServiceId parseServiceId(Node node) throws Exception {
        XroadObjectType objectType = getObjectType(node);
        if (objectType.equals(XroadObjectType.CENTRALSERVICE)) {
            XroadCentralServiceIdentifierType type =
                    parseType(XroadObjectType.CENTRALSERVICE, node,
                            XroadCentralServiceIdentifierType.class);
            return IdentifierTypeConverter.parseCentralServiceId(type);
        } else {
            XroadServiceIdentifierType type =
                    parseType(XroadObjectType.SERVICE, node,
                            XroadServiceIdentifierType.class);
            return IdentifierTypeConverter.parseServiceId(type);
        }
    }

    // -- Helper methods ------------------------------------------------------

    static <T> T parseType(XroadObjectType expectedType, Node node,
            Class<T> clazz) throws Exception {
        verifyObjectType(node, expectedType);

        Unmarshaller unmarshaller = JAXB_CTX.createUnmarshaller();
        JAXBElement<T> element = unmarshaller.unmarshal(node, clazz);
        return element.getValue();
    }

    static void verifyObjectType(Node node, XroadObjectType expected)
            throws Exception {
        XroadObjectType type = getObjectType(node);
        if (!expected.equals(type)) {
            throw new CodedException(X_INVALID_XML,
                    "Unexpected objectType: %s", type);
        }
    }

    static XroadObjectType getObjectType(Node node) throws Exception {
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
            return XroadObjectType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            throw new CodedException(X_INVALID_XML,
                    "Unknown objectType: %s", typeName);
        }
    }
}
