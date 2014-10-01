package ee.cyber.xroad.mediator.service.wsdlmerge.structure;

import java.io.IOException;
import java.util.List;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stringtemplate.v4.ST;

@Data
public class Message implements Marshallable, TemplateAware {

    private static final Logger LOG = LoggerFactory.getLogger(Message.class);

    private final String name;
    private final List<MessagePart> parts;
    private final boolean xrdStandardHeader;

    @Override
    public String getXml() throws IOException {
        ST template = getTemplate();

        template.add("name", name);
        template.add("parts", parts);

        String result = template.render();
        LOG.trace("WSDL message:\n{}", result);

        return result;
    }

    @Override
    public ST getTemplate() throws IOException {
        return TemplateUtils.getTemplate("marshal-Message.st");
    }
}
