package ee.cyber.sdsb.proxy.testsuite.testcases;

import java.util.Arrays;
import java.util.Collection;

import ee.cyber.sdsb.common.conf.GlobalConf;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.SslMessageTestCase;
import ee.cyber.sdsb.proxy.testsuite.TestGlobalConf;

import static ee.cyber.sdsb.common.ErrorCodes.SERVER_CLIENTPROXY_X;
import static ee.cyber.sdsb.common.ErrorCodes.X_NETWORK_ERROR;

/**
 * Tests that correct error occurs when none of the hosts can be connected to.
 */
public class SslSelectFastestProxyNoConnections extends SslMessageTestCase {
    public SslSelectFastestProxyNoConnections() {
        requestFileName = "getstate.query";
        responseFileName = "getstate.answer";
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
