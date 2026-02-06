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

import ee.ria.xroad.common.util.MimeTypes;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.proxy.core.protocol.ProxyMessage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.apache.commons.io.IOUtils.toInputStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ResponseStaxSoapParserImplTest {

    private static final String HASH_ELEM_START = "<xroad:requestHash ";
    private static final String HASH_ELEM_END = "</xroad:requestHash>";
    private static final String ID_ELEM_START = "<xroad:id>";
    private static final String ID_ELEM_END = "</xroad:id>";

    private static final String HASH_ELEM = """
            <xroad:requestHash algorithmId="http://www.w3.org/2001/04/xmlenc#sha512">aGFzaA==</xroad:requestHash>
            """.trim();

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ProxyMessage request;


    @ParameterizedTest
    @ValueSource(strings = {
            "basic",
            "basic-no-formating",
            "basic-with-bom",
            "basic-with-random-hash",
            "basic-with-random2-hash",
            "basic-with-encoded-symbols"
    })
    public void basicCases(String file) throws IOException {
        when(request.getSoap().getHash()).thenReturn("hash".getBytes(StandardCharsets.UTF_8));
        var xml = asString(file);

        var result = new ResponseStaxSoapParserImpl(request).parse(MimeTypes.TEXT_XML_UTF8, toInputStream(xml, "utf-8"));

        assertEquals(addHash(xml), result.getXml());
    }

    @Test
    public void basicMissingQueryId() throws IOException {
        var result = new ResponseStaxSoapParserImpl(request).parse(MimeTypes.TEXT_XML_UTF8, asInputStream("fault-missing-query-id"));

        assertEquals(asString("fault-missing-query-id"), result.getXml());
    }

    private static InputStream asInputStream(String name) {
        return ResponseStaxSoapParserImplTest.class.getResourceAsStream("/soap-responses/%s.xml".formatted(name));
    }

    private static String asString(String name) throws IOException {
        return IOUtils.resourceToString("/soap-responses/%s.xml".formatted(name), StandardCharsets.UTF_8);
    }

    private static String addHash(String xml) {
        var builder = new StringBuilder(xml);
        if (builder.charAt(0) == '\ufeff') {
            builder.deleteCharAt(0);
        }
        var hStart = builder.indexOf(HASH_ELEM_START);
        if (hStart > 0) {
            var hEnd = builder.indexOf(HASH_ELEM_END) + HASH_ELEM_END.length();
            builder.replace(hStart, hEnd, "");
            var whiteIdx = hStart - 1;
            while (Character.isWhitespace(builder.charAt(whiteIdx))) {
                builder.deleteCharAt(whiteIdx);
                whiteIdx--;
            }
        }
        var iStart = builder.indexOf(ID_ELEM_START);
        if (iStart > 0) {
            var iEnd = builder.indexOf(ID_ELEM_END) + ID_ELEM_END.length();
            var whiteIdx = iStart - 1;
            var indent = new StringBuilder();
            while (Character.isWhitespace(builder.charAt(whiteIdx))) {
                indent.append(builder.charAt(whiteIdx));
                whiteIdx--;
            }
            builder.insert(iEnd, indent.reverse() + HASH_ELEM);
        }
        return builder.toString();
    }
}
