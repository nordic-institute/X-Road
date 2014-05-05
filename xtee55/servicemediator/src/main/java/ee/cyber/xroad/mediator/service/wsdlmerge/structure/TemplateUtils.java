package ee.cyber.xroad.mediator.service.wsdlmerge.structure;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.stringtemplate.v4.ST;

public class TemplateUtils {

    public static ST getTemplate(String templateFile)
            throws IOException {
        try (InputStream templateStream =
                TemplateUtils.class.getClassLoader().getResourceAsStream(
                        templateFile);) {
            String templateContent = IOUtils.toString(templateStream).trim();

            return new ST(templateContent, '$', '$');
        }
    }
}
