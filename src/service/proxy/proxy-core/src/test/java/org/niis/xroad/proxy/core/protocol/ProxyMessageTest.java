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
package org.niis.xroad.proxy.core.protocol;

import ee.ria.xroad.common.message.SoapMessageImpl;

import org.junit.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class ProxyMessageTest {

    public static final byte[] MOCK_SOAP_MESSAGE_BODY = "<mock-soap-message-body-xml/>".getBytes(UTF_8);

    @Test
    public void soapContentTypeMimeEncodedSoap() {
        var originalContentType = "multipart/related";

        ProxyMessage message = new ProxyMessage(originalContentType);

        assertThat(message.getSoapContentType()).isEqualTo(originalContentType);
    }

    @Test
    public void soapContentTypeAttachment() throws Exception {
        var originalContentType = "original-content-type";

        ProxyMessage message = new ProxyMessage(originalContentType);
        message.attachment("application/octet-stream", new ByteArrayInputStream("attachment".getBytes(UTF_8)), Map.of());

        assertThat(message.getSoapContentType()).isEqualTo(originalContentType);
    }

    @Test
    public void soapContentTypeTextXml() {
        var originalContentType = "application/xml";

        ProxyMessage message = new ProxyMessage(originalContentType);

        assertThat(message.getSoapContentType()).isEqualTo("text/xml; charset=UTF-8");
    }


    @Test
    public void soapContent() throws Exception {
        ProxyMessage message = new ProxyMessage("text/xml; charset=UTF-8");
        message.soap(getMockSoapMessage(), Map.of());

        var outputStream = new ByteArrayOutputStream();
        message.writeSoapContent(outputStream);
        assertThat(outputStream.toByteArray())
                .withRepresentation(bytes -> new String((byte[]) bytes))
                .isEqualTo(MOCK_SOAP_MESSAGE_BODY);
    }

    @Test
    public void soapContentMime() throws Exception {
        var expectedSoapContent = """
                --BOUNDARY\r
                content-type: text/xml; charset=UTF-8\r
                \r
                <mock-soap-message-body-xml/>\r
                --BOUNDARY--\r
                """;

        ProxyMessage message = new ProxyMessage("multipart/related; boundary=BOUNDARY");
        message.soap(getMockSoapMessage(), Map.of());

        var outputStream = new ByteArrayOutputStream();
        message.writeSoapContent(outputStream);

        assertThat(outputStream.toByteArray())
                .withRepresentation(bytes -> new String((byte[]) bytes))
                .isEqualTo(expectedSoapContent.getBytes(UTF_8));
    }

    @Test
    public void soapContentWithAttachment() throws Exception {
        var expectedSoapContent = """
                --BOUNDARY\r
                content-type:text/xml; charset=UTF-8\r
                \r
                <mock-soap-message-body-xml/>\r
                --BOUNDARY\r
                content-type:text/plain\r
                \r
                attachment\r
                --BOUNDARY--\r
                """;

        ProxyMessage message = new ProxyMessage("multipart/related; boundary=BOUNDARY");
        message.soap(getMockSoapMessage(), Map.of());
        message.attachment("text/plain", new ByteArrayInputStream("attachment".getBytes(UTF_8)), Map.of());

        var outputStream = new ByteArrayOutputStream();
        message.writeSoapContent(outputStream);

        assertThat(outputStream.toByteArray())
                .withRepresentation(bytes -> new String((byte[]) bytes))
                .isEqualTo(expectedSoapContent.getBytes(UTF_8));
    }

    private SoapMessageImpl getMockSoapMessage() {
        var mockSoapMessage = Mockito.mock(SoapMessageImpl.class);
        when(mockSoapMessage.getContentType()).thenReturn("text/xml; charset=UTF-8");
        when(mockSoapMessage.getBytes()).thenReturn(MOCK_SOAP_MESSAGE_BODY);
        return mockSoapMessage;
    }
}
