package ee.cyber.sdsb.common.identifier;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.namespace.QName;

import org.w3c.dom.Node;

public class IdentifierXmlNodePrinter {

    private static final JAXBContext jaxbCtx = initJaxbContext();

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
        return jaxbCtx.createMarshaller();
    }

    private static JAXBContext initJaxbContext() {
        try {
            return JAXBContext.newInstance(ObjectFactory.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
