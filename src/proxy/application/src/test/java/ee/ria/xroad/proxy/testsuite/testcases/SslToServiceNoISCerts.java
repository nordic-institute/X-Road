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

import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.ProxyTestSuite;
import ee.ria.xroad.proxy.testsuite.SslMessageTestCase;
import ee.ria.xroad.proxy.testsuite.TestSuiteServerConf;

import static ee.ria.xroad.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.X_SERVICE_FAILED_X;
import static ee.ria.xroad.common.ErrorCodes.X_SSL_AUTH_FAILED;

/**
 * ServerProxy connects to Service using SSL, serverconf contains no IS certs.
 *
 * Response contains error code:
 *      Server.ServerProxy.ServiceFailed.SslAuthenticationFailed
 */
public class SslToServiceNoISCerts extends SslMessageTestCase {

    /**
     * Constructs the test case.
     */
    public SslToServiceNoISCerts() {
        requestFileName = "getstate.query";
        responseFile = "getstate.answer";
    }

    @Override
    protected void startUp() throws Exception {
        super.startUp();

        ServerConf.reload(new TestSuiteServerConf() {
            @Override
            public boolean isSslAuthentication(ServiceId service) {
                return true;
            }
        });
    }

    @Override
    public String getServiceAddress(ServiceId service) {
        return "https://127.0.0.1:" + ProxyTestSuite.SERVICE_SSL_PORT;
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse)
            throws Exception {
        assertErrorCode(SERVER_SERVERPROXY_X, X_SERVICE_FAILED_X,
                X_SSL_AUTH_FAILED);
    }

}
