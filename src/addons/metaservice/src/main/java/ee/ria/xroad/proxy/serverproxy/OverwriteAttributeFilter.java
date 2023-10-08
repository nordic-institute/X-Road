/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.proxy.serverproxy;

import lombok.extern.slf4j.Slf4j;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.XMLFilterImpl;

import javax.xml.namespace.QName;

/**
 * Filter that replaces specific attributes values with given strings
 */
@Slf4j
public class OverwriteAttributeFilter extends XMLFilterImpl {
    private String newValue;
    private QName element;
    private QName attributeToOverwrite;

    private static final QName WSDL_SOAP_ADDRESS_ELEMENT = new QName(
            "http://schemas.xmlsoap.org/wsdl/soap/", "address");

    private static final QName WSDL_SOAP_ADDRESS_LOCATION_ATTRIBUTE = new QName(
            null, "location");

    /**
     * Create filter which replaces given attribute
     * @param attributeToOverwrite
     * @param newValue
     */
    public OverwriteAttributeFilter(QName element, QName attributeToOverwrite, String newValue) {
        super();
        this.element = element;
        this.attributeToOverwrite = attributeToOverwrite;
        this.newValue = newValue;
    }

    /**
     * Create filter which replaces soap:address element's location attribute
     * @param newValue
     */
    public static OverwriteAttributeFilter createOverwriteSoapAddressFilter(String newValue) {
        return new OverwriteAttributeFilter(WSDL_SOAP_ADDRESS_ELEMENT,
                WSDL_SOAP_ADDRESS_LOCATION_ATTRIBUTE, newValue);
    }


    @Override
    public void startElement(String uri, String localName, String qName,
                             Attributes atts)
            throws SAXException {
        QName elementQName = new QName(uri, localName);
        if (elementQName.equals(element)) {
            // replace attributes with new copies
            AttributesImpl newAttributes = new AttributesImpl();
            for (int i = 0; i < atts.getLength(); i++) {
                String attributeValue;
                QName attributeQName = new QName(atts.getURI(i), atts.getLocalName(i));
                if (attributeToOverwrite.equals(attributeQName)) {
                    attributeValue = newValue;
                } else {
                    attributeValue = atts.getValue(i);
                }
                newAttributes.addAttribute(atts.getURI(i), atts.getLocalName(i),
                        atts.getQName(i), atts.getType(i), attributeValue);
            }
            // Delegate to the normal parsing behavior
            super.startElement(uri, localName, qName, newAttributes);
        } else {
            super.startElement(uri, localName, qName, atts);
        }
    }
}
