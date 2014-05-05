package ee.cyber.xroad.mediator.service.wsdlmerge.structure;

import java.io.IOException;
import java.util.List;

import lombok.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stringtemplate.v4.ST;

@Value
public class Message implements Marshallable {

    private static final Logger LOG = LoggerFactory.getLogger(Message.class);

    private String name;
    private List<MessagePart> parts;

    @Override
    public String getXml() throws IOException {
        ST template = TemplateUtils.getTemplate("marshal-Message.st");

        template.add("name", name);
        template.add("parts", parts);

        String result = template.render();
        LOG.trace("WSDL message:\n{}", result);

        return result;
    }
}
