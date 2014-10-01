package ee.cyber.sdsb.proxy.testsuite.testcases;

import java.security.cert.X509Certificate;
import java.util.List;

import ee.cyber.sdsb.common.TestCertUtil;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.ServiceId;
import ee.cyber.sdsb.common.conf.serverconf.ServerConf;
import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.ProxyTestSuite;
import ee.cyber.sdsb.proxy.testsuite.SslMessageTestCase;
import ee.cyber.sdsb.proxy.testsuite.TestServerConf;

import static ee.cyber.sdsb.common.ErrorCodes.*;
import static java.util.Collections.singletonList;

/**
 * ServerProxy connects to Service using SSL, serverconf contains wrong
 * IS cert.
 *
 * Response contains error code:
 *      Server.ServerProxy.ServiceFailed.SslAuthenticationFailed
 */
public class SslToServiceWrongISCert extends SslMessageTestCase {
    public SslToServiceWrongISCert() {
        requestFileName = "getstate.query";
        responseFileName = "getstate.answer";
    }

    @Override
    protected void startUp() throws Exception {
        super.startUp();

        ServerConf.reload(new TestServerConf() {
            @Override
            public boolean isSslAuthentication(ServiceId service) {
                return true;
            }

            @Override
            public List<X509Certificate> getIsCerts(ClientId client)
                    throws Exception {
                return singletonList(TestCertUtil.getProducer().cert);
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
