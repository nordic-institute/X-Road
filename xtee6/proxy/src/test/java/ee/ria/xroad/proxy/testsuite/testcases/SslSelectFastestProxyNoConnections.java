package ee.ria.xroad.proxy.testsuite.testcases;

import java.util.Arrays;
import java.util.Collection;

import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.SslMessageTestCase;
import ee.ria.xroad.proxy.testsuite.TestGlobalConf;

import static ee.ria.xroad.common.ErrorCodes.SERVER_CLIENTPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.X_NETWORK_ERROR;

/**
 * Tests that correct error occurs when none of the hosts can be connected to.
 */
public class SslSelectFastestProxyNoConnections extends SslMessageTestCase {

    /**
     * Constructs the test case.
     */
    public SslSelectFastestProxyNoConnections() {
        requestFileName = "getstate.query";
        responseFile = "getstate.answer";
    }

    @Override
    protected void startUp() throws Exception {
        super.startUp();

        GlobalConf.reload(new TestGlobalConf() {
            @Override
            public Collection<String> getProviderAddress(ClientId provider) {
                return Arrays.asList("foo", "bar", "baz");
            }
        });
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse)
            throws Exception {
        assertErrorCodeStartsWith(SERVER_CLIENTPROXY_X, X_NETWORK_ERROR);
    }
}
