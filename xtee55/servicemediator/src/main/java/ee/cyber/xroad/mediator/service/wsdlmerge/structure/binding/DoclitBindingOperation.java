package ee.cyber.xroad.mediator.service.wsdlmerge.structure.binding;

import java.io.IOException;
import java.util.List;

import ee.cyber.xroad.mediator.service.wsdlmerge.structure.Marshallable;
import ee.cyber.xroad.mediator.service.wsdlmerge.structure.TemplateUtils;

import org.stringtemplate.v4.ST;

/**
 * Abstraction for WSDL document/literal binding operation.
 */
public class DoclitBindingOperation extends BindingOperation {

    /**
     * Creates document/literal binding operation.
     *
     * @param name operation name.
     * @param version operation version
     * @param xrdNodes operation X-Road specific nodes.
     */
    public DoclitBindingOperation(
            String name, String version, List<Marshallable> xrdNodes) {
        super(name, version, xrdNodes);
    }

    @Override
    public ST getTemplate() throws IOException {
        return TemplateUtils.getTemplate("marshal-BindingOperation-doclit.st");
    }
}
