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
package org.niis.xroad.proxy.core.serverproxy;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.ProtocolVersion;
import ee.ria.xroad.common.message.RepresentedParty;
import ee.ria.xroad.common.message.SoapHeader;
import ee.ria.xroad.common.util.MimeTypes;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.proxy.core.protocol.ProxyMessage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.apache.commons.io.IOUtils.toInputStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResponseStaxSoapParserImplTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ProxyMessage request;


    @ParameterizedTest
    @CsvSource(value = {
            "basic,expected-basic",
            "basic2,expected-basic2",
            "basic3,expected-basic3",
            "basic4,expected-basic4",
            "basic-no-formatting,expected-basic-no-formatting",
            "basic-with-bom,expected-basic",
            "basic-with-random-hash,expected-basic",
            "basic-with-random-hash2,expected-basic",
            "basic-with-random-hash3,expected-basic",
            "basic-with-random-hash4,expected-basic",
            "basic-with-encoded-symbols,expected-basic-with-encoded-symbols",
            "basic-cdata,expected-basic-cdata"
    })
    void basicCases(String file, String expectedFile) throws IOException {
        when(request.getSoap().getHash()).thenReturn("hash".getBytes(StandardCharsets.UTF_8));
        var xml = asString(file);

        var result = new ResponseStaxSoapParserImpl(request, false).parse(MimeTypes.TEXT_XML_UTF8, toInputStream(xml, "utf-8"));

        assertEquals(asString(expectedFile).stripTrailing(), result.getXml());
    }

    @Test
    void basicMissingQueryId() throws IOException {
        var result = new ResponseStaxSoapParserImpl(request, false)
                .parse(MimeTypes.TEXT_XML_UTF8, asInputStream("fault-missing-query-id"));

        assertEquals(asString("fault-missing-query-id").stripTrailing(), result.getXml());
    }

    /**
     * With auto-injection on but a header already present, behavior is unchanged: no synthesis, the
     * request hash is still rewritten via the {@code xrd:id} trigger and appears exactly once
     * (covers a response carrying a bogus existing request hash).
     */
    @ParameterizedTest
    @CsvSource(value = {
            "basic,expected-basic",
            "basic-with-random-hash,expected-basic"
    })
    void headerPresentWithAutoInjectOnIsUnchanged(String file, String expectedFile) throws IOException {
        when(request.getSoap().getHash()).thenReturn("hash".getBytes(StandardCharsets.UTF_8));

        var result = new ResponseStaxSoapParserImpl(request, true)
                .parse(MimeTypes.TEXT_XML_UTF8, toInputStream(asString(file), "utf-8"));

        assertEquals(asString(expectedFile).stripTrailing(), result.getXml());
        assertThat(countOccurrences(result.getXml(), "algorithmId")).isEqualTo(1);
    }

    /**
     * Legacy SOAP response without an X-Road header: with auto-injection on, the header is synthesized
     * from the originating request before the body. Covers the SOAP-ENV prefix, a short prefix, and the
     * default (prefix-less) SOAP namespace.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "no-header,expected-no-header",
            "no-header-s-prefix,expected-no-header-s-prefix",
            "no-header-default-ns,expected-no-header-default-ns"
    })
    void autoInjectMissingHeader(String file, String expectedFile) throws IOException {
        stubRequestHeader(fullRequestHeader());

        var result = new ResponseStaxSoapParserImpl(request, true)
                .parse(MimeTypes.TEXT_XML_UTF8, toInputStream(asString(file), "utf-8"));

        assertEquals(asString(expectedFile).stripTrailing(), result.getXml());
    }

    @Test
    void autoInjectMissingHeaderWithoutUserId() throws IOException {
        SoapHeader header = fullRequestHeader();
        header.setUserId(null);
        stubRequestHeader(header);

        var result = new ResponseStaxSoapParserImpl(request, true)
                .parse(MimeTypes.TEXT_XML_UTF8, toInputStream(asString("no-header"), "utf-8"));

        assertEquals(asString("expected-no-header-no-user-id").stripTrailing(), result.getXml());
    }

    @Test
    void autoInjectMissingHeaderWithRepresentedParty() throws IOException {
        SoapHeader header = fullRequestHeader();
        header.setRepresentedParty(new RepresentedParty("COM", "12345"));
        stubRequestHeader(header);

        var result = new ResponseStaxSoapParserImpl(request, true)
                .parse(MimeTypes.TEXT_XML_UTF8, toInputStream(asString("no-header"), "utf-8"));

        assertEquals(asString("expected-no-header-represented-party").stripTrailing(), result.getXml());
    }

    @Test
    void autoInjectMissingHeaderWritesExactlyOneRequestHash() throws IOException {
        stubRequestHeader(fullRequestHeader());

        var result = new ResponseStaxSoapParserImpl(request, true)
                .parse(MimeTypes.TEXT_XML_UTF8, toInputStream(asString("no-header"), "utf-8"));

        assertThat(countOccurrences(result.getXml(), "algorithmId")).isEqualTo(1);
        assertThat(result.getXml()).contains("aGFzaA==");
    }

    @Test
    void missingHeaderStillFailsWhenAutoInjectDisabled() {
        assertThatThrownBy(() -> new ResponseStaxSoapParserImpl(request, false)
                .parse(MimeTypes.TEXT_XML_UTF8, asInputStream("no-header")))
                .isInstanceOf(XrdRuntimeException.class);
    }

    private void stubRequestHeader(SoapHeader header) {
        when(request.getSoap().getHeader()).thenReturn(header);
        when(request.getSoap().getHash()).thenReturn("hash".getBytes(StandardCharsets.UTF_8));
    }

    private static SoapHeader fullRequestHeader() {
        SoapHeader header = new SoapHeader();
        header.setClient(ClientId.Conf.create("EE", "BUSINESS", "consumer"));
        header.setService(ServiceId.Conf.create("EE", "BUSINESS", "producer", null, "testQuery"));
        header.setQueryId("1234567890");
        header.setUserId("user-id-1");
        header.setProtocolVersion(new ProtocolVersion("4.0"));
        return header;
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private static InputStream asInputStream(String name) {
        return ResponseStaxSoapParserImplTest.class.getResourceAsStream("/soap-responses/%s.xml".formatted(name));
    }

    private static String asString(String name) throws IOException {
        return IOUtils.resourceToString("/soap-responses/%s.xml".formatted(name), StandardCharsets.UTF_8);
    }
}
