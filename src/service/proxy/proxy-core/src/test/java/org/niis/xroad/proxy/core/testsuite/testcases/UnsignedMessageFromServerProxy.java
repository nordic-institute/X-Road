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
package org.niis.xroad.proxy.core.testsuite.testcases;

import org.niis.xroad.proxy.core.test.Message;
import org.niis.xroad.proxy.core.test.MessageTestCase;
import org.niis.xroad.proxy.core.testsuite.UsingDummyServerProxy;

import static ee.ria.xroad.common.ErrorCodes.SERVER_CLIENTPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.X_MISSING_SIGNATURE;

/**
 * Client sends normal request. We emulate server proxy and send multipart
 * that does not contain signature.
 * Result: CP sends error.
 */
public class UnsignedMessageFromServerProxy extends MessageTestCase implements UsingDummyServerProxy {

    /**
     * Constructs the test case.
     */
    public UnsignedMessageFromServerProxy() {
        requestFileName = "getstate.query";

        responseFile = "no-signature.query";
        responseContentType = "multipart/mixed; charset=UTF-8; "
                + "boundary=jetty42534330h7vzfqv2";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_CLIENTPROXY_X, X_MISSING_SIGNATURE);
    }
}
