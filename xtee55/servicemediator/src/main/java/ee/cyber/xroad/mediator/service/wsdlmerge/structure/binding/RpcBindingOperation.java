package ee.cyber.xroad.mediator.service.wsdlmerge.structure.binding;

import java.io.IOException;
import java.util.List;

import ee.cyber.xroad.mediator.service.wsdlmerge.structure.Marshallable;
import ee.cyber.xroad.mediator.service.wsdlmerge.structure.TemplateUtils;

import org.stringtemplate.v4.ST;

public class RpcBindingOperation extends BindingOperation {

    private String targetNamespace;

    public RpcBindingOperation(String name, String version,
            List<Marshallable> xrdNodes, String targetNamespace) {
        super(name, version, xrdNodes);
        this.targetNamespace = targetNamespace;
    }

    @Override
    public ST getTemplate() throws IOException {
        return TemplateUtils.getTemplate("marshal-BindingOperation-rpc.st");
    }

    @Override
    protected void addPlaceholders(ST template) {
        super.addPlaceholders(template);
        template.add("tns", targetNamespace);
    }
}
