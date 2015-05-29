package ee.cyber.xroad.mediator.service.wsdlmerge.structure;

import java.io.IOException;
import java.util.List;

import lombok.Data;
import org.stringtemplate.v4.ST;

import ee.cyber.xroad.mediator.service.wsdlmerge.structure.binding.Binding;

/**
 * Description of single WSDL
 */
@Data
public class WSDL implements Marshallable, TemplateAware {
    private final List<XrdNode> schemaElements;
    private final List<Message> messages;
    private final List<PortType> portTypes;
    private final List<Binding> bindings;
    private final List<Service> services;
    private final String xrdNamespace;
    private final String targetNamespace;
    private final String name;

    @Override
    public String getXml() throws Exception {
        ST template = getTemplate();

        template.add("name", name);
        template.add("tns", targetNamespace);
        template.add("schemaElements", schemaElements);
        template.add("messages", messages);
        template.add("portTypes", portTypes);
        template.add("bindings", bindings);
        template.add("services", services);

        return template.render();
    }

    @Override
    public ST getTemplate() throws IOException {
        return TemplateUtils.getTemplate("marshal-WSDL.st");
    }
}
