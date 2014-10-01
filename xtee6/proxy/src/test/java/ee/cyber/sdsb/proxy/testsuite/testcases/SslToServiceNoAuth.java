package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.common.identifier.ServiceId;
import ee.cyber.sdsb.common.conf.serverconf.ServerConf;
import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.ProxyTestSuite;
import ee.cyber.sdsb.proxy.testsuite.SslMessageTestCase;
import ee.cyber.sdsb.proxy.testsuite.TestServerConf;

/**
 * ServerProxy connects to Service using SSL.
 */
public class SslToServiceNoAuth extends SslMessageTestCase {
    public SslToServiceNoAuth() {
        requestFileName = "getstate.query";
        responseFileName = "getstate.answer";
    }

    @Override
    protected void startUp() throws Exception {
        super.startUp();

        ServerConf.reload(new TestServerConf() {
            @Override
            public boolean isSslAuthentication(ServiceId service) {
                return false;
            }
        });
    }

    @Override
    public String getServiceAddress(ServiceId service) {
        return "https://127.0.0.1:" + ProxyTestSuite.SERVICE_SSL_PORT;
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse)
            throws Exception {
        // Normal response, nothing more to check here.
    }
}
