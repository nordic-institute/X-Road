package ee.cyber.xroad.mediator.service.wsdlmerge.structure;

import java.io.StringWriter;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.w3c.dom.Node;

@EqualsAndHashCode
public class XrdNode implements Marshallable {
    @Getter
    private Node node;

    public XrdNode(Node node) {
        this.node = node;
    }

    @Override
    public String getXml() throws Exception {
        StringWriter writer = new StringWriter();
        Transformer transformer = TransformerFactory.newInstance()
                .newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.transform(new DOMSource(node), new StreamResult(writer));
        return writer.toString();
    }
}
