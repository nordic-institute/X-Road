/**
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

import ee.ria.xroad.common.message.JaxbUtils;

import org.w3c.dom.Node;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.namespace.QName;

/**
 * XML element generator for X-Road identifiers.
 */
public final class IdentifierXmlNodePrinter {

    private static final JAXBContext JAXB_CTX =
            JaxbUtils.initJAXBContext(ObjectFactory.class);

    private IdentifierXmlNodePrinter() {
    }

    /**
     * Generates an XML element for a client ID as a child for the provided node.
     * @param clientId ID of the client that needs to be converted into an XML node
     * @param parentNode the parent of the newly generated node
     * @param nodeQName qualified name of the newly generated node
     * @throws Exception if errors occur during XML node generation
     */
    public static void printClientId(ClientId clientId, Node parentNode,
            QName nodeQName) throws Exception {
        if (clientId == null) {
            return;
        }

        XRoadClientIdentifierType type =
                IdentifierTypeConverter.printClientId(clientId);

        JAXBElement<XRoadClientIdentifierType> jaxbElement =
                new JAXBElement<XRoadClientIdentifierType>(
                        nodeQName, XRoadClientIdentifierType.class, type);

        getMarshaller().marshal(jaxbElement, parentNode);
    }

    /**
     * Generates an XML element for a service ID as a child for the provided node.
     * @param serviceId ID of the service that needs to be converted into an XML node
     * @param parentNode the parent of the newly generated node
     * @param nodeQName qualified name of the newly generated node
     * @throws Exception if errors occur during XML node generation
     */
    public static void printServiceId(ServiceId serviceId, Node parentNode,
            QName nodeQName) throws Exception {
        if (serviceId == null) {
            return;
        }

        XRoadServiceIdentifierType type =
                IdentifierTypeConverter.printServiceId(serviceId);

        JAXBElement<XRoadServiceIdentifierType> jaxbElement =
                new JAXBElement<XRoadServiceIdentifierType>(
                        nodeQName, XRoadServiceIdentifierType.class, type);

        getMarshaller().marshal(jaxbElement, parentNode);
    }

    private static Marshaller getMarshaller() throws Exception {
        return JAXB_CTX.createMarshaller();
    }
}
