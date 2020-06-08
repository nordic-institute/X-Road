/**
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
package ee.ria.xroad.common.message;

import ee.ria.xroad.common.util.MimeTypes;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.util.MimeUtils.mpRelatedContentType;

/**
 * Encodes SOAP with attachments as MIME multipart.
 */
public class MultipartSoapMessageEncoder implements SoapMessageEncoder {

    private MultipartOutputStream multipart;

    /**
     * Creates a SOAP message encoder that writes messages to the given
     * output stream.
     * @param output output stream to write SOAP messages
     */
    public MultipartSoapMessageEncoder(OutputStream output) {
        this(output, null);
    }

    /**
     * Creates a SOAP message encoder that writes messages to the given
     * output stream.
     * @param output output stream to write SOAP messages
     * @param boundary the MIME boundary value to use
     */
    public MultipartSoapMessageEncoder(OutputStream output, String boundary) {
        if (boundary == null) {
            multipart = new MultipartOutputStream(output);
        } else {
            multipart = new MultipartOutputStream(output, boundary);
        }
    }

    /**
     * @return the content-type string for multipart/related content with the
     * current boundary.
     */
    @Override
    public String getContentType() {
        return mpRelatedContentType(multipart.getBoundary(),
                MimeTypes.TEXT_XML);
    }

    @Override
    public void close() throws IOException {
        multipart.close();
    }

    @Override
    public void soap(SoapMessage soapMessage,
                     Map<String, String> additionalHeaders) throws Exception {
        multipart.startPart(soapMessage.getContentType(),
                convertHeaders(additionalHeaders));
        multipart.write(soapMessage.getBytes());
    }

    @Override
    public void attachment(String contentType, InputStream content,
                           Map<String, String> additionalHeaders) throws Exception {
        String[] headers = {};
        if (additionalHeaders != null && !additionalHeaders.isEmpty()) {
            headers = convertHeaders(additionalHeaders);
        }

        multipart.startPart(contentType, headers);
        IOUtils.copy(content, multipart);
    }

    private static String[] convertHeaders(Map<String, String> headers) {
        return headers.entrySet().stream()
            .map(e -> e.getKey() + ": " + e.getValue())
            .collect(Collectors.toList())
            .toArray(new String[] {});
    }
}
