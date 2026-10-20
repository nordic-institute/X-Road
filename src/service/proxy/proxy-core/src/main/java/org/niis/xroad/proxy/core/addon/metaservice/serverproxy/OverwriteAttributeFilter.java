/*
 * The MIT License
 *
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
package org.niis.xroad.proxy.core.addon.metaservice.serverproxy;

import lombok.extern.slf4j.Slf4j;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.XMLFilterImpl;

import javax.xml.namespace.QName;

import java.util.Set;

/**
 * Filter that replaces the endpoint addresses of a WSDL port with a given string: the location attribute of
 * SOAP 1.1, SOAP 1.2 and HTTP binding address elements, and the text of WS-Addressing Address elements
 * inside a port.
 */
@Slf4j
public final class OverwriteAttributeFilter extends XMLFilterImpl {
    private static final QName WSDL_PORT_ELEMENT = new QName("http://schemas.xmlsoap.org/wsdl/", "port");

    private static final Set<QName> ADDRESS_ELEMENTS = Set.of(
            new QName("http://schemas.xmlsoap.org/wsdl/soap/", "address"),
            new QName("http://schemas.xmlsoap.org/wsdl/soap12/", "address"),
            new QName("http://schemas.xmlsoap.org/wsdl/http/", "address"));

    private static final Set<QName> WS_ADDRESSING_ADDRESS_ELEMENTS = Set.of(
            new QName("http://www.w3.org/2005/08/addressing", "Address"),
            new QName("http://schemas.xmlsoap.org/ws/2004/08/addressing", "Address"));

    private static final QName LOCATION_ATTRIBUTE = new QName(null, "location");

    private final String newValue;
    private int portDepth;
    private int addressDepth;
    private boolean addressTextWritten;

    private OverwriteAttributeFilter(String newValue) {
        super();
        this.newValue = newValue;
    }

    /**
     * Create filter which replaces the endpoint addresses of WSDL ports
     * @param newValue the replacement address
     */
    public static OverwriteAttributeFilter createOverwriteSoapAddressFilter(String newValue) {
        return new OverwriteAttributeFilter(newValue);
    }

    @Override
    public void startElement(String uri, String localName, String qName,
                             Attributes atts)
            throws SAXException {
        QName elementQName = new QName(uri, localName);
        if (WSDL_PORT_ELEMENT.equals(elementQName)) {
            portDepth++;
        }
        if (portDepth > 0 && WS_ADDRESSING_ADDRESS_ELEMENTS.contains(elementQName)) {
            addressDepth++;
            addressTextWritten = false;
        }
        if (ADDRESS_ELEMENTS.contains(elementQName)) {
            // replace attributes with new copies
            AttributesImpl newAttributes = new AttributesImpl();
            for (int i = 0; i < atts.getLength(); i++) {
                String attributeValue;
                QName attributeQName = new QName(atts.getURI(i), atts.getLocalName(i));
                if (LOCATION_ATTRIBUTE.equals(attributeQName)) {
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

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        QName elementQName = new QName(uri, localName);
        if (addressDepth > 0 && WS_ADDRESSING_ADDRESS_ELEMENTS.contains(elementQName)) {
            addressDepth--;
        }
        if (WSDL_PORT_ELEMENT.equals(elementQName)) {
            portDepth--;
        }
        super.endElement(uri, localName, qName);
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (addressDepth == 0) {
            super.characters(ch, start, length);
        } else if (!addressTextWritten) {
            addressTextWritten = true;
            char[] replacement = newValue.toCharArray();
            super.characters(replacement, 0, replacement.length);
        }
    }
}
