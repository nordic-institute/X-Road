package ee.cyber.xroad.mediator.service.wsdlmerge.parser;

import java.util.List;

import javax.wsdl.extensions.ExtensibilityElement;
import javax.wsdl.extensions.UnknownExtensibilityElement;
import javax.wsdl.extensions.soap.SOAPAddress;
import javax.wsdl.extensions.soap.SOAPBinding;
import javax.xml.namespace.QName;

public class ExtensibilityElementAttributeParser
        extends ExtensibilityElementParser {
    private String attributeName;

    public ExtensibilityElementAttributeParser(
            List<?> extensibilityElements,
            QName searchableElement) {
        super(extensibilityElements, searchableElement);
    }

    public ExtensibilityElementAttributeParser(
            List<?> extensibilityElements,
            QName searchableElement, String attributeName) {
        this(extensibilityElements, searchableElement);
        this.attributeName = attributeName;
    }

    @Override
    protected String parseContent(
            ExtensibilityElement element) {
        if (element instanceof UnknownExtensibilityElement) {
            return ((UnknownExtensibilityElement) element)
                    .getElement().getAttribute(attributeName);
        } else if (element instanceof SOAPAddress) {
            return ((SOAPAddress) element).getLocationURI();
        } else if (element instanceof SOAPBinding) {
            return ((SOAPBinding) element).getStyle();
        }

        return null;
    }
}
