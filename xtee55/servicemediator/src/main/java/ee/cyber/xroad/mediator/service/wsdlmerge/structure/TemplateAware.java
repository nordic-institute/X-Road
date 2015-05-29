package ee.cyber.xroad.mediator.service.wsdlmerge.structure;

import java.io.IOException;

import org.stringtemplate.v4.ST;

/**
 * Implemented by WSDL elements that use {@link ST} templates to render
 * themselves.
 */
public interface TemplateAware {
    /**
     * Returns template used to render this object.
     *
     * @return template used for rendering.
     * @throws IOException thrown when template cannot be returned.
     */
    ST getTemplate() throws IOException;
}
