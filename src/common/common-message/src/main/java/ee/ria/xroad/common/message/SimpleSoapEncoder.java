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
package ee.ria.xroad.common.message;

import ee.ria.xroad.common.util.MimeTypes;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * Simple SOAP encoder that does not support attachments or additional headers.
 */
public class SimpleSoapEncoder implements SoapMessageEncoder {

    private final OutputStream outputStream;


    public SimpleSoapEncoder(OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    @Override
    public String getContentType() {
        return MimeTypes.TEXT_XML_UTF8;
    }

    @Override
    public void close() throws IOException {
        outputStream.close();
    }

    @Override
    public void soap(SoapMessage message, Map<String, String> additionalHeaders) throws Exception {
        if (additionalHeaders != null && additionalHeaders.size() > 0) {
            throw new IllegalArgumentException("Additional headers not supported!");
        }
        outputStream.write(message.getBytes());
    }

    @Override
    public void attachment(String contentType, InputStream content,
                           Map<String, String> additionalHeaders) throws Exception {
        throw new UnsupportedOperationException("Attachments are not supported!");
    }

}
