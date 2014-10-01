package ee.cyber.xroad.mediator.service.wsdlmerge.structure;

import java.io.IOException;
import java.util.List;

import javax.xml.namespace.QName;

import lombok.Data;
import org.stringtemplate.v4.ST;

@Data
public class ServicePort implements Marshallable, TemplateAware {
    private final QName binding;
    private final String name;
    private final List<Marshallable> xrdNodes;

    @Override
    public String getXml() throws Exception {
        ST template = getTemplate();

        template.add("binding", binding);
        template.add("name", name);
        template.add("xrdNodes", xrdNodes);

        return template.render();
    }

    @Override
    public ST getTemplate() throws IOException {
        return TemplateUtils.getTemplate("marshal-ServicePort.st");
    }
}
