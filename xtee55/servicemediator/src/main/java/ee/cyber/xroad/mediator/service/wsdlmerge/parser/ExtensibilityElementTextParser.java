package ee.cyber.xroad.mediator.service.wsdlmerge.parser;

import java.util.List;

import javax.wsdl.extensions.ExtensibilityElement;
import javax.wsdl.extensions.UnknownExtensibilityElement;
import javax.xml.namespace.QName;

/**
 * Parses text from extensibility elements.
 */
public class ExtensibilityElementTextParser extends ExtensibilityElementParser {

    /**
     * Creates extensibility element text parser.
     *
     * @param extensibilityElements extensibility elements to parse.
     * @param searchableElement element to be searched.
     */
    public ExtensibilityElementTextParser(List<?> extensibilityElements,
            QName searchableElement) {
        super(extensibilityElements, searchableElement);
    }

    @Override
    protected String parseContent(ExtensibilityElement element) {
        if (!(element instanceof UnknownExtensibilityElement)) {
            return null;
        }

        return ((UnknownExtensibilityElement) element).getElement()
                .getTextContent();
    }
}
