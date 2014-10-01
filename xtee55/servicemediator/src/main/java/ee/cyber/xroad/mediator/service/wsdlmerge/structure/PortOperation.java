package ee.cyber.xroad.mediator.service.wsdlmerge.structure;

import java.io.IOException;
import java.util.List;

import javax.xml.namespace.QName;

import lombok.Data;
import org.stringtemplate.v4.ST;

@Data
public class PortOperation implements Marshallable, TemplateAware {
    private final String name;
    private final QName input;
    private final QName output;
    private final List<Marshallable> documentation;

    @Override
    public String getXml() throws Exception {
        ST template = getTemplate();

        template.add("name", name);
        template.add("input", input);
        template.add("output", output);
        template.add("doc", documentation);

        return template.render();
    }

    @Override
    public ST getTemplate() throws IOException {
        return TemplateUtils.getTemplate("marshal-PortOperation.st");
    }
}
