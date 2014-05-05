package ee.cyber.xroad.mediator.service.wsdlmerge.parser;

import java.util.List;

import javax.wsdl.extensions.ExtensibilityElement;
import javax.wsdl.extensions.UnknownExtensibilityElement;
import javax.xml.namespace.QName;

public class ExtensibilityElementTextParser extends ExtensibilityElementParser {

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
