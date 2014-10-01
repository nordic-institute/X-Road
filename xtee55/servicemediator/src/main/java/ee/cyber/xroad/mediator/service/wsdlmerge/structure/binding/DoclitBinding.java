package ee.cyber.xroad.mediator.service.wsdlmerge.structure.binding;

import java.io.IOException;
import java.util.List;

import javax.xml.namespace.QName;

import ee.cyber.xroad.mediator.service.wsdlmerge.structure.TemplateUtils;

import org.stringtemplate.v4.ST;

public class DoclitBinding extends Binding {

    public DoclitBinding(
            String name, QName type, List<BindingOperation> operations) {
        super(name, type, operations);
    }

    @Override
    public ST getTemplate() throws IOException {
        return TemplateUtils.getTemplate("marshal-Binding-doclit.st");
    }
}
