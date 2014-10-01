package ee.cyber.xroad.mediator.service.wsdlmerge.structure.binding;

import java.util.List;

import lombok.Data;
import org.stringtemplate.v4.ST;

import ee.cyber.xroad.mediator.service.wsdlmerge.structure.Marshallable;
import ee.cyber.xroad.mediator.service.wsdlmerge.structure.TemplateAware;

@Data
public abstract class BindingOperation implements Marshallable, TemplateAware {
    protected final String name;
    protected final String version;
    protected final List<Marshallable> xrdNodes;

    @Override
    public String getXml() throws Exception {
        ST template = getTemplate();

        addPlaceholders(template);

        return template.render();
    }

    protected void addPlaceholders(ST template) {
        template.add("name", name);
        template.add("xrdNodes", xrdNodes);
    }
}
