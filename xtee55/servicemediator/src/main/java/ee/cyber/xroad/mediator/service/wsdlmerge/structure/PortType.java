package ee.cyber.xroad.mediator.service.wsdlmerge.structure;

import java.io.IOException;
import java.util.List;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stringtemplate.v4.ST;

/**
 * Abstraction for WSDL port type.
 */
@Data
public class PortType implements Marshallable, TemplateAware {

    private static final Logger LOG = LoggerFactory.getLogger(PortType.class);

    private final String name;
    private final List<PortOperation> operations;

    @Override
    public String getXml() throws Exception {
        ST template = getTemplate();

        template.add("name", name);
        template.add("ops", operations);

        String result = template.render();
        LOG.trace("WSDL port type:\n{}", result);

        return result;
    }

    @Override
    public ST getTemplate() throws IOException {
        return TemplateUtils.getTemplate("marshal-PortType.st");
    }
}
