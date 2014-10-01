package ee.cyber.sdsb.common.identifier;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.namespace.QName;

import org.w3c.dom.Node;

import ee.cyber.sdsb.common.message.JaxbUtils;

public class IdentifierXmlNodePrinter {

    private static final JAXBContext JAXB_CTX =
            JaxbUtils.initJAXBContext(ObjectFactory.class);

    public static void printClientId(ClientId clientId, Node parentNode,
            QName nodeQName) throws Exception {
        if (clientId == null) {
            return;
        }

        SdsbClientIdentifierType type =
                IdentifierTypeConverter.printClientId(clientId);

        JAXBElement<SdsbClientIdentifierType> jaxbElement =
                new JAXBElement<SdsbClientIdentifierType>(
                        nodeQName, SdsbClientIdentifierType.class, type);

        getMarshaller().marshal(jaxbElement, parentNode);
    }

    public static void printServiceId(ServiceId serviceId, Node parentNode,
            QName nodeQName) throws Exception {
        if (serviceId == null) {
            return;
        }

        SdsbServiceIdentifierType type =
                IdentifierTypeConverter.printServiceId(serviceId);

        JAXBElement<SdsbServiceIdentifierType> jaxbElement =
                new JAXBElement<SdsbServiceIdentifierType>(
                        nodeQName, SdsbServiceIdentifierType.class, type);

        getMarshaller().marshal(jaxbElement, parentNode);
    }

    private static Marshaller getMarshaller() throws Exception {
        return JAXB_CTX.createMarshaller();
    }
}
