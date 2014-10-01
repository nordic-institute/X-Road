package ee.cyber.xroad.mediator.service.wsdlmerge.structure;

import java.io.IOException;

import javax.xml.namespace.QName;

import lombok.Data;
import org.stringtemplate.v4.ST;

@Data
public class MessagePart implements Marshallable, TemplateAware {
    private final String name;
    private final QName element;
    private final QName type;

    @Override
    public String getXml() throws IOException {
        ST template = getTemplate();

        template.add("name", name);
        template.add("elem", element);

        // 'type' seems to be reserved keyword for ST - it does not work as 
        // expected.
        template.add("type_", type);

        return template.render();
    }

    @Override
    public ST getTemplate() throws IOException {
        return TemplateUtils.getTemplate("marshal-MessagePart.st");
    }
}
