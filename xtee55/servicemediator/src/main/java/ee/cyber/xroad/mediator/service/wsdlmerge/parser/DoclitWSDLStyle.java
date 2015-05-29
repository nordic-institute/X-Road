package ee.cyber.xroad.mediator.service.wsdlmerge.parser;


import javax.wsdl.Part;
import javax.xml.namespace.QName;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import ee.cyber.xroad.mediator.service.wsdlmerge.structure.Marshallable;
import ee.cyber.xroad.mediator.service.wsdlmerge.structure.binding.Binding;
import ee.cyber.xroad.mediator.service.wsdlmerge.structure.binding.BindingOperation;
import ee.cyber.xroad.mediator.service.wsdlmerge.structure.binding.DoclitBinding;
import ee.cyber.xroad.mediator.service.wsdlmerge.structure.binding.DoclitBindingOperation;

/**
 * Encapsulates WSDL style specific parsing logic.
 */
class DoclitWSDLStyle {
    private static final String XSD_NS =
            "http://www.w3.org/2001/XMLSchema";

    private final String xrdNamespace;

    DoclitWSDLStyle(String xrdNamespace) {
        this.xrdNamespace = xrdNamespace;
    }

    QName[] getValidSchemaElementQNames() {
        return new QName[] {new QName(XSD_NS, "element")};
    }

    Map<String, QName> getStandardHeaderParts() {
        Map<String, QName> result = new HashMap<>();

        result.put("consumer", new QName(xrdNamespace, "consumer"));
        result.put("producer", new QName(xrdNamespace, "producer"));
        result.put("userId", new QName(xrdNamespace, "userId"));
        result.put("service", new QName(xrdNamespace, "service"));
        result.put("id", new QName(xrdNamespace, "id"));

        return result;
    }

    Binding getBinding(String name, QName type,
                                 List<BindingOperation> operations) {
        return new DoclitBinding(name, type, operations);
    }

    BindingOperation getBindingOperations(String name,
                                                    String version, List<Marshallable> xrdNodes) {
        return new DoclitBindingOperation(name, version, xrdNodes);
    }

    boolean isSchemaElement(Node element) {
        String elementLocalName = element.getLocalName();
        if (StringUtils.isBlank(elementLocalName)) {
            return false;
        }

        QName elementQName = new QName(
                element.getNamespaceURI(), elementLocalName);

        for (QName each : getValidSchemaElementQNames()) {
            if (each.equals(elementQName)) {
                return true;
            }
        }

        NamedNodeMap attributes = element.getAttributes();

        if (attributes == null) {
            return false;
        }

        Node nameNode = attributes.getNamedItem("name");

        return (nameNode != null
                && StringUtils.isNotBlank(nameNode.getTextContent()));
    }

    @SuppressWarnings("unchecked")
    boolean isXrdStandardHeader(javax.wsdl.Message wsdlMessage) {
        Map<String, QName> messageParts = new HashMap<>();
        for (Object each : wsdlMessage.getParts().entrySet()) {
            Map.Entry<String, Part> partEntry =
                    (Map.Entry<String, Part>) each;
            String partName = partEntry.getKey();
            Part part = partEntry.getValue();
            messageParts.put(partName, part.getElementName());
        }

        for (Map.Entry<String, QName> entry
                : getStandardHeaderParts().entrySet()) {
            String standardHeaderPartName = entry.getKey();
            QName standardHeaderPartElement = entry.getValue();

            QName messagePartName = messageParts
                    .get(standardHeaderPartName);

            if (!messagePartExists(standardHeaderPartElement,
                    messagePartName)) {
                return false;
            }
        }

        return true;
    }

    private boolean messagePartExists(QName standardHeaderPartElement,
                                      QName messagePartName) {
        return messagePartName != null
                && standardHeaderPartElement.equals(messagePartName);
    }
}
