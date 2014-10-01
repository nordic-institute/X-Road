package ee.cyber.sdsb.proxy.testsuite;

import java.util.Set;

import ee.cyber.sdsb.common.TestCertUtil;
import ee.cyber.sdsb.common.TestCertUtil.PKCS12;
import ee.cyber.sdsb.common.conf.serverconf.InternalSSLKey;
import ee.cyber.sdsb.common.conf.serverconf.IsAuthentication;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.SecurityCategoryId;
import ee.cyber.sdsb.common.identifier.ServiceId;

public class TestServerConf extends EmptyServerConf {

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
