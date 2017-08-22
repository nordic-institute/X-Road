package ee.ria.xroad.proxy.serverproxy;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.XMLFilterImpl;

/**
 *
 */
public class OverwriteSoapAddressFilter extends XMLFilterImpl {
    // TODOOOO: QName instead of local name
    private String attributeToRename;
    private String newValue;

    /**
     *
     * @param attributeToOverwrite
     * @param newValue
     */
    public OverwriteSoapAddressFilter(String attributeToOverwrite, String newValue) {
        super();
        this.attributeToRename = attributeToOverwrite;
        this.newValue = newValue;
    }

    @Override
    public void startElement(String uri, String localName, String qName,
                             Attributes atts)
            throws SAXException {
        // process attributes
        // Make a copy that we can modify
        AttributesImpl newAtts = new AttributesImpl();
        for (int i = 0; i < atts.getLength(); i++) {
            String attributeValue;
            // replace value for matching attributes
            if (attributeToRename.equals(atts.getLocalName(i))) {
                attributeValue = newValue;
            } else {
                attributeValue = atts.getValue(i);
            }
            newAtts.addAttribute(atts.getURI(i), atts.getLocalName(i),
                    atts.getQName(i), atts.getType(i), attributeValue);
        }
        // Delegate to the normal parsing behavior
        super.startElement(uri, localName, qName, newAtts);
    }

}
