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
package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.util.MimeUtils.contentTypeWithCharset;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.eclipse.jetty.http.MimeTypes.TEXT_XML;

/**
 * Client sends normal request, server responds with invalid content type.
 * Result: SP sends error
 */
public class NonUtf8ResponseContentType extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public NonUtf8ResponseContentType() {
        requestFileName = "getstate.query";

        responseFile = "getstate-iso88591.answer";
        responseContentType = TEXT_XML;

        responseServiceContentType =
                contentTypeWithCharset(TEXT_XML, ISO_8859_1.name());

        // Currently the 'getstate.answer' contains different encoding -- should this be an error?
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse)
            throws Exception {
    }
}
