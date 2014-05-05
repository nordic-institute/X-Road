package ee.cyber.xroad.mediator.service.wsdlmerge.structure;

import java.io.IOException;

import javax.xml.namespace.QName;

import lombok.Value;
import org.stringtemplate.v4.ST;

@Value
public class MessagePart implements Marshallable {
    private String name;
    private QName element;
    private QName type;

    @Override
    public String getXml() throws IOException {
        ST template = TemplateUtils.getTemplate("marshal-MessagePart.st");

        template.add("name", name);
        template.add("elem", element);

        // 'type' seems to be reserved keyword for ST - it does not work as 
        // expected.
        template.add("type_", type);

        return template.render();
    }
}
