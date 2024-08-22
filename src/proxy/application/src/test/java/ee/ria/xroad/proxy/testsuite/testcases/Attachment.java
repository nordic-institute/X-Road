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
package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

/**
 * Client sends message with attachment. Server responds with normal message.
 * Result: all OK.
 */
public class Attachment extends MessageTestCase {

    private static final String CLIENT_HEADER_NAME = "Client-Header";
    private static final String CLIENT_HEADER_VALUE = "FooBar";

    /**
     * Constructs the test case.
     */
    public Attachment() {
        requestFileName = "attachm.query";
        requestContentType = "multipart/related; charset=UTF-8; "
                + "boundary=jetty771207119h3h10dty";

        responseFile = "attachm.answer";
    }

    @Override
    protected void onServiceReceivedRequest(Message receivedRequest) throws Exception {
        super.onServiceReceivedRequest(receivedRequest);

        String clientHeaderValue =
                receivedRequest.getMultipartHeaders().get(2).get(
                        CLIENT_HEADER_NAME);

        if (!CLIENT_HEADER_VALUE.equals(clientHeaderValue)) {
            throw new RuntimeException(String.format(
                    "Expected client header '%s' with value '%s', but got '%s'",
                    CLIENT_HEADER_NAME, CLIENT_HEADER_VALUE,
                    clientHeaderValue));
        }
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse) {
        // Not expecting anything in particular.
    }
}
