package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.ProxyTestSuite;
import ee.ria.xroad.proxy.testsuite.SslMessageTestCase;
import ee.ria.xroad.proxy.testsuite.TestServerConf;

/**
 * ServerProxy connects to Service using SSL.
 */
public class SslToServiceNoAuth extends SslMessageTestCase {

    /**
     * Constructs the test case.
     */
    public SslToServiceNoAuth() {
        requestFileName = "getstate.query";
        responseFile = "getstate.answer";
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
