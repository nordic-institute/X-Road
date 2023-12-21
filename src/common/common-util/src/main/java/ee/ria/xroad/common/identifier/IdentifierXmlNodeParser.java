/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.message.JaxbUtils;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.Unmarshaller;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

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
        XRoadClientIdentifierType type =
                parseType(XRoadObjectType.MEMBER, node,
                        XRoadClientIdentifierType.class);
        return IdentifierTypeConverter.parseClientId(type);
    }

    /**
     * Parses a client or subsystem ID from the given node.
     * @param node the node from which to parse the client ID
     * @return ClientId
     * @throws Exception if errors occur during parsing
     */
    public static ClientId parseClientOrSubsystem(Node node) throws Exception {

        XRoadObjectType type = getObjectType(node);
        if (!XRoadObjectType.MEMBER.equals(type) && !XRoadObjectType.SUBSYSTEM.equals(type)) {
            throw new CodedException(X_INVALID_XML,
                    "Unexpected objectType: %s", type);
        }
        XRoadClientIdentifierType systemType =
                parseType(type, node,
                        XRoadClientIdentifierType.class);
        return IdentifierTypeConverter.parseClientId(systemType);
    }

    /**
     * Parses a service ID from the given node.
     * @param node the node from which to parse the service ID
     * @return ServiceId
     * @throws Exception if errors occur during parsing
     */
    public static ServiceId parseServiceId(Node node) throws Exception {
        XRoadServiceIdentifierType type =
                parseType(XRoadObjectType.SERVICE, node,
                        XRoadServiceIdentifierType.class);
        return IdentifierTypeConverter.parseServiceId(type);
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
