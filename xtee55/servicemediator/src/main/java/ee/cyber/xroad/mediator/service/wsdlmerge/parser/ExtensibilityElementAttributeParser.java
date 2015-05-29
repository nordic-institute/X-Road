package ee.cyber.xroad.mediator.service.wsdlmerge.parser;

import javax.wsdl.extensions.ExtensibilityElement;
import javax.wsdl.extensions.soap.SOAPAddress;
import javax.wsdl.extensions.soap.SOAPBinding;
import javax.xml.namespace.QName;
import java.util.List;

/**
 * Gets values of extensibility element attributes.
 */
public class ExtensibilityElementAttributeParser
        extends ExtensibilityElementParser {
    /**
     * Creates parser for extensibility element attributes.
     * @param extensibilityElements list of extensibility elements.
     * @param searchableElement element to be searched.
     */
    public ExtensibilityElementAttributeParser(
            List<?> extensibilityElements,
            QName searchableElement) {
        super(extensibilityElements, searchableElement);
    }

    @Override
    protected String parseContent(
            ExtensibilityElement element) {
        if (element instanceof SOAPAddress) {
            return ((SOAPAddress) element).getLocationURI();
        } else if (element instanceof SOAPBinding) {
            return ((SOAPBinding) element).getStyle();
        }

        return null;
    }
}
