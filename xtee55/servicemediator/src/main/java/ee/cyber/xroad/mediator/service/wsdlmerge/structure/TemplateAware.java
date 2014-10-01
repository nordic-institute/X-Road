package ee.cyber.xroad.mediator.service.wsdlmerge.structure;

import java.io.IOException;

import org.stringtemplate.v4.ST;

/**
 * Implemented by WSDL elements that use {@link ST} templates to render
 * themselves.
 */
public interface TemplateAware {
    public ST getTemplate() throws IOException;
}
