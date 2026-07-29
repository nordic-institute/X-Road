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

package ee.ria.xroad.common.message;

import ee.ria.xroad.common.util.MimeTypes;

import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoapUtilsTest {

    static Collection<Object[]> soapActionData() {
        return Arrays.asList(new Object[][]{
                {null, true},
                {"", true},
                {"\"\"", true},
                {"\"http://example.org/test\"", true},
                {"\"urn:foo\"", true},
                {"http://quotes/missing", false},
                {"\"http://extra/quote\"\"", false},
                {"\"spaces \"", false}
        });
    }

    @DisplayName("Validate SOAP Action Header")
    @ParameterizedTest(name = "{index}: <{0}>, valid: {1}")
    @MethodSource("soapActionData")
    void testValidateSoapAction(String header, boolean expected) {
        boolean valid = true;
        try {
            SoapUtils.validateSoapActionHeader(header);
        } catch (XrdRuntimeException e) {
            valid = false;
        }
        assertEquals(expected, valid);
    }

    @Test
    void createSOAPMessageParsesDoctypeFreeXml() throws Exception {
        SOAPMessage soap = parse("""
                <?xml version="1.0" encoding="UTF-8"?>
                <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
                    <SOAP-ENV:Body><test/></SOAP-ENV:Body>
                </SOAP-ENV:Envelope>""");

        assertNotNull(soap.getSOAPBody());
    }

    @Test
    void createSOAPMessageRejectsExternalEntityDoctype() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE SOAP-ENV:Envelope [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
                    <SOAP-ENV:Body><test>&xxe;</test></SOAP-ENV:Body>
                </SOAP-ENV:Envelope>""";

        SOAPException e = assertThrows(SOAPException.class, () -> parse(xml));
        assertTrue(e.getMessage().contains("DOCTYPE"));
    }

    @Test
    void createSOAPMessageRejectsInternalEntityExpansion() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE lolz [
                    <!ENTITY lol "lol">
                    <!ENTITY lol2 "&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;">
                    <!ENTITY lol3 "&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;">
                ]>
                <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
                    <SOAP-ENV:Body><test>&lol3;</test></SOAP-ENV:Body>
                </SOAP-ENV:Envelope>""";

        assertThrows(SOAPException.class, () -> parse(xml));
    }

    @Test
    void createSOAPMessageRejectsExternalDtdReference() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE SOAP-ENV:Envelope SYSTEM "http://localhost/evil.dtd">
                <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
                    <SOAP-ENV:Body><test/></SOAP-ENV:Body>
                </SOAP-ENV:Envelope>""";

        assertThrows(SOAPException.class, () -> parse(xml));
    }

    @Test
    void soapParserRejectsDoctype() {
        byte[] xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE SOAP-ENV:Envelope [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
                    <SOAP-ENV:Body><test>&xxe;</test></SOAP-ENV:Body>
                </SOAP-ENV:Envelope>""".getBytes(UTF_8);

        SoapParserImpl parser = new SoapParserImpl();
        InputStream input = new ByteArrayInputStream(xml);

        assertThrows(XrdRuntimeException.class, () -> parser.parse(MimeTypes.TEXT_XML_UTF8, input));
    }

    private static SOAPMessage parse(String xml) throws SOAPException, IOException {
        return SoapUtils.createSOAPMessage(new ByteArrayInputStream(xml.getBytes(UTF_8)), "UTF-8");
    }
}
