package ee.cyber.xroad.mediator.service.wsdlmerge.structure.binding;

import java.util.List;

import javax.xml.namespace.QName;

import lombok.Data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stringtemplate.v4.ST;

import ee.cyber.xroad.mediator.service.wsdlmerge.structure.Marshallable;
import ee.cyber.xroad.mediator.service.wsdlmerge.structure.TemplateAware;

@Data
public abstract class Binding implements Marshallable, TemplateAware {

    private static final Logger LOG = LoggerFactory.getLogger(Binding.class);

    protected final String name;
    protected final QName type;
    protected final List<BindingOperation> operations;

    @Override
    public String getXml() throws Exception {
        ST template = getTemplate();

        template.add("name", name);
        template.add("type", type);
        template.add("ops", operations);

        String result = template.render();
        LOG.trace("WSDL binding:\n{}", result);

        return result;
    }
}
