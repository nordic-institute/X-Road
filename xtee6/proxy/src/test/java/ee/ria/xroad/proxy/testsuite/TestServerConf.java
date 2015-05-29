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
