/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.util.MultiPartOutputStream;

import static ee.ria.xroad.common.util.MimeUtils.contentTypeWithCharset;
import static ee.ria.xroad.common.util.MimeUtils.mpRelatedContentType;
import static org.eclipse.jetty.http.MimeTypes.TEXT_XML;

/**
 * Encodes SOAP with attachments as MIME multipart.
 */
public class SoapMessageEncoder implements SoapMessageConsumer, Closeable {

    private MultiPartOutputStream multipart;

    /**
     * Creates a SOAP message encoder that writes messages to the given output stream.
     * @param output output stream to write SOAP messages
     * @throws Exception if a MIME multipart stream could not be created
     */
    public SoapMessageEncoder(OutputStream output) throws Exception {
        multipart = new MultiPartOutputStream(output);
    }

    /**
     * Gets the content-type string for multipart/related content with the current boundary.
     * @return String
     */
    public String getContentType() {
        return mpRelatedContentType(multipart.getBoundary());
    }

    @Override
    public void close() throws IOException {
        multipart.close();
    }

    @Override
    public void soap(SoapMessage soapMessage) throws Exception {
        String charset = soapMessage.getCharset();
        multipart.startPart(contentTypeWithCharset(TEXT_XML, charset));
        multipart.write(soapMessage.getBytes());
    }

    @Override
    public void attachment(String contentType, InputStream content,
            Map<String, String> additionalHeaders) throws Exception {
        String[] headers = null;
        if (additionalHeaders != null && !additionalHeaders.isEmpty()) {
            headers = convertHeaders(additionalHeaders);
        }

        multipart.startPart(contentType, headers);
        IOUtils.copy(content, multipart);
    }

    private static String[] convertHeaders(Map<String, String> headers) {
        String[] ret = new String[headers.size()];
        int idx = 0;
        for (Map.Entry<String, String> entry: headers.entrySet()) {
            ret[idx++] = entry.getKey() + ": " + entry.getValue();
        }

        return ret;
    }
}
