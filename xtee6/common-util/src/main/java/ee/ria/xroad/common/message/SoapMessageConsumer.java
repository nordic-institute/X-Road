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

import java.io.InputStream;
import java.util.Map;

/**
 * Describes the SOAP message callback. The message consists of a XML message
 * and optional attachments.
 */
public interface SoapMessageConsumer {

    /**
     * Called when SOAP message is parsed.
     * @param message the SOAP message
     * @throws Exception if an error occurs
     */
    void soap(SoapMessage message) throws Exception;

    /**
     * Called when an attachment is received.
     * @param contentType the content type of the attachment
     * @param content the input stream holding the attachment data
     * @param additionalHeaders any additional headers for the attachment
     * @throws Exception if an error occurs
     */
    void attachment(String contentType, InputStream content,
            Map<String, String> additionalHeaders) throws Exception;
}
