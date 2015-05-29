package ee.ria.xroad.common.identifier;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.namespace.QName;

import org.w3c.dom.Node;

import ee.ria.xroad.common.message.JaxbUtils;

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

        XroadClientIdentifierType type =
                IdentifierTypeConverter.printClientId(clientId);

        JAXBElement<XroadClientIdentifierType> jaxbElement =
                new JAXBElement<XroadClientIdentifierType>(
                        nodeQName, XroadClientIdentifierType.class, type);

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

        XroadServiceIdentifierType type =
                IdentifierTypeConverter.printServiceId(serviceId);

        JAXBElement<XroadServiceIdentifierType> jaxbElement =
                new JAXBElement<XroadServiceIdentifierType>(
                        nodeQName, XroadServiceIdentifierType.class, type);

        getMarshaller().marshal(jaxbElement, parentNode);
    }

    private static Marshaller getMarshaller() throws Exception {
        return JAXB_CTX.createMarshaller();
    }
}
