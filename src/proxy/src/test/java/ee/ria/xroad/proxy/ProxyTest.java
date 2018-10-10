/**
 * The MIT License
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
package ee.ria.xroad.proxy;

import org.junit.Before;
import org.junit.Test;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import static org.hamcrest.Matchers.hasItems;
import static org.junit.Assert.assertThat;

/**
 * Junit tests for X-Road proxies
 */
public class ProxyTest {

    Proxy proxy;

    /**
     * Tests that creating SSL Context doesn't throw
     * @throws NoSuchAlgorithmException
     * @throws KeyManagementException
     */
    @Test
    public void createSSLContext() throws NoSuchAlgorithmException, KeyManagementException {
        proxy.createSSLContext();
    }

    /**
     * Tests that accepted tls cipher suites are supported
     * @throws Exception
     */
    @Test
    public void getAcceptedCipherSuites() throws Exception {
        String[] supportedCiphers = proxy.createSSLContext().createSSLEngine().getSupportedCipherSuites();
        String[] acceptedCiphers = proxy.getAcceptedCipherSuites();
        assertThat(Arrays.asList(supportedCiphers), hasItems(acceptedCiphers));
    }

    @Before
    public void init() {
        proxy = new Proxy() {
            @Override
            public void start() throws Exception {
                // noop
            }

            @Override
            public void stop() throws Exception {
                // noop
            }

            @Override
            public void join() throws InterruptedException {
                // noop
            }
        };
    }
}
