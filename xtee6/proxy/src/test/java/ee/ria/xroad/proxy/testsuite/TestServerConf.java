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
package ee.ria.xroad.proxy.testsuite;

import java.util.Set;

import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.TestCertUtil.PKCS12;
import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.conf.serverconf.IsAuthentication;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityCategoryId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;

/**
 * Test serverconf implementation.
 */
public class TestServerConf extends EmptyServerConf {

    @Override
    public SecurityServerId getIdentifier() {
        return SecurityServerId.create("EE", "BUSINESS", "consumer",
                "proxytest");
    }

    @Override
    public String getServiceAddress(ServiceId service) {
        String serviceAddress = currentTestCase().getServiceAddress(service);
        if (serviceAddress != null) {
            return serviceAddress;
        }

        return "127.0.0.1:" + ProxyTestSuite.SERVICE_PORT
                + ((service != null) ? "/" + service.getServiceCode() : "");
    }

    @Override
    public boolean serviceExists(ServiceId service) {
        return currentTestCase().serviceExists(service);
    }

    @Override
    public boolean isQueryAllowed(ClientId sender, ServiceId service) {
        return currentTestCase().isQueryAllowed(sender, service);
    }

    @Override
    public String getDisabledNotice(ServiceId service) {
        return currentTestCase().getDisabledNotice(service);
    }

    @Override
    public Set<SecurityCategoryId> getRequiredCategories(ServiceId service) {
        return currentTestCase().getRequiredCategories(service);
    }

    @Override
    public InternalSSLKey getSSLKey() throws Exception {
        PKCS12 consumer = TestCertUtil.getConsumer();
        return new InternalSSLKey(consumer.key, consumer.cert);
    }

    @Override
    public IsAuthentication getIsAuthentication(ClientId client) {
        return IsAuthentication.NOSSL;
    }

    private static MessageTestCase currentTestCase() {
        return ProxyTestSuite.currentTestCase;
    }
}
