package ee.cyber.xroad.mediator.service.wsdlmerge.structure;

import java.io.StringWriter;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import lombok.Data;
import org.w3c.dom.Node;

/**
 * Abstraction for X-Road specific WSDL node.
 */
@Data
public class XrdNode implements Marshallable {
    private final Node node;

    @Override
    public String getXml() throws Exception {
        StringWriter writer = new StringWriter();
        Transformer transformer = TransformerFactory.newInstance()
                .newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.transform(new DOMSource(node), new StreamResult(writer));
        return writer.toString().trim();
    }

    /**
     * Returns name of the node.
     *
     * @return node name.
     */
    public String getName() {
        Node nameElement = node.getAttributes().getNamedItem("name");
        if (nameElement == null) {
            return null;
        }

        return nameElement.getTextContent();
    }
}
