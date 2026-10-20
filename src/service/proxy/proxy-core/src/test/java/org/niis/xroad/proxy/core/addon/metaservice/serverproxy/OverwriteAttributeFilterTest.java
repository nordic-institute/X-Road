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
package org.niis.xroad.proxy.core.addon.metaservice.serverproxy;

import org.junit.jupiter.api.Test;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

class OverwriteAttributeFilterTest {

    private static final String REPLACEMENT = "http://example.org/replacement";
    private static final String WSDL_NS = "http://schemas.xmlsoap.org/wsdl/";
    private static final String SOAP_NS = "http://schemas.xmlsoap.org/wsdl/soap/";
    private static final String WSA_NS = "http://www.w3.org/2005/08/addressing";
    private static final String OTHER_NS = "http://example.org/other";

    private static final String NAMESPACES = " xmlns:wsdl=\"" + WSDL_NS + "\" xmlns:soap=\"" + SOAP_NS
            + "\" xmlns:wsa=\"" + WSA_NS + "\" xmlns:o=\"" + OTHER_NS + "\"";

    @Test
    void shouldMaskAddressTextDeliveredInSeveralChunks() throws SAXException {
        var recorder = new Recorder();
        var filter = newFilter(recorder);

        filter.startElement(WSDL_NS, "port", "wsdl:port", new AttributesImpl());
        filter.startElement(WSA_NS, "Address", "wsa:Address", new AttributesImpl());
        for (String chunk : new String[] {"http://inte", "rnal.exam", "ple/svc"}) {
            filter.characters(chunk.toCharArray(), 0, chunk.length());
        }
        filter.endElement(WSA_NS, "Address", "wsa:Address");
        filter.endElement(WSDL_NS, "port", "wsdl:port");

        assertThat(recorder.result()).isEqualTo("<port><Address>" + REPLACEMENT + "</Address></port>");
    }

    @Test
    void shouldKeepAddressOutsideWsdlPortUnchanged() throws Exception {
        var result = filter("<wsdl:definitions" + NAMESPACES + ">"
                + "<wsa:EndpointReference><wsa:Address>http://keep.example/svc</wsa:Address></wsa:EndpointReference>"
                + "</wsdl:definitions>");

        assertThat(result).isEqualTo("<definitions><EndpointReference><Address>http://keep.example/svc</Address>"
                + "</EndpointReference></definitions>");
    }

    @Test
    void shouldMaskCdataAndSurroundingWhitespaceInPortAddress() throws Exception {
        var result = filter("<wsdl:port" + NAMESPACES + ">"
                + "<wsa:Address>\n  <![CDATA[http://internal.example/svc]]>\n</wsa:Address>"
                + "</wsdl:port>");

        assertThat(result).isEqualTo("<port><Address>" + REPLACEMENT + "</Address></port>");
    }

    @Test
    void shouldKeepEmptyPortAddressEmpty() throws Exception {
        var result = filter("<wsdl:port" + NAMESPACES + "><wsa:Address/></wsdl:port>");

        assertThat(result).isEqualTo("<port><Address></Address></port>");
    }

    @Test
    void shouldKeepAttributesOtherThanLocationOnAddressElement() throws Exception {
        var result = filter("<wsdl:port" + NAMESPACES + ">"
                + "<soap:address location=\"http://internal.example/svc\" binding=\"b1\" extra=\"e1\"/>"
                + "</wsdl:port>");

        assertThat(result).isEqualTo("<port><address binding=\"b1\" extra=\"e1\" location=\"" + REPLACEMENT
                + "\"></address></port>");
    }

    @Test
    void shouldKeepAddressElementsOfOtherNamespaceUnchanged() throws Exception {
        var result = filter("<wsdl:port" + NAMESPACES + ">"
                + "<o:address location=\"http://keep.example/svc\"/>"
                + "<o:Address>http://keep.example/text</o:Address>"
                + "</wsdl:port>");

        assertThat(result).isEqualTo("<port><address location=\"http://keep.example/svc\"></address>"
                + "<Address>http://keep.example/text</Address></port>");
    }

    private static OverwriteAttributeFilter newFilter(Recorder recorder) {
        var filter = OverwriteAttributeFilter.createOverwriteSoapAddressFilter(REPLACEMENT);
        filter.setContentHandler(recorder);
        return filter;
    }

    private static String filter(String xml) throws ParserConfigurationException, SAXException, IOException {
        var factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XMLReader reader = factory.newSAXParser().getXMLReader();
        var recorder = new Recorder();
        var filter = newFilter(recorder);
        filter.setParent(reader);
        filter.parse(new InputSource(new StringReader(xml)));
        return recorder.result();
    }

    private static final class Recorder extends DefaultHandler {
        private final StringBuilder out = new StringBuilder();

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) {
            var sortedAttributes = new TreeMap<String, String>();
            for (int i = 0; i < atts.getLength(); i++) {
                if (!atts.getQName(i).startsWith("xmlns")) {
                    sortedAttributes.put(atts.getLocalName(i), atts.getValue(i));
                }
            }
            out.append('<').append(localName);
            sortedAttributes.forEach((name, value) -> out.append(' ').append(name).append("=\"").append(value).append('"'));
            out.append('>');
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            out.append("</").append(localName).append('>');
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            out.append(ch, start, length);
        }

        String result() {
            return out.toString();
        }
    }
}
