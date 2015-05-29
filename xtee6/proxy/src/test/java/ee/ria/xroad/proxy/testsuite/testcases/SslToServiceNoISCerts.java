package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.ProxyTestSuite;
import ee.ria.xroad.proxy.testsuite.SslMessageTestCase;
import ee.ria.xroad.proxy.testsuite.TestServerConf;

import static ee.ria.xroad.common.ErrorCodes.*;

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

        ServerConf.reload(new TestServerConf() {
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
