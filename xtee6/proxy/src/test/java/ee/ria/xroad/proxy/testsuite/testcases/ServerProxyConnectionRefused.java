package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.SERVER_CLIENTPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.X_NETWORK_ERROR;

/**
 * Client sends normal message. The CP gets connection refused error
 * when connecting to SP.
 * Result: CP responds with error
 */
public class ServerProxyConnectionRefused extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public ServerProxyConnectionRefused() {
        requestFileName = "getstate.query";
    }

    @Override
    public String getProviderAddress(String providerName) {
        // Nobody listens to port 5555 on this address.
        return "127.0.0.3";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_CLIENTPROXY_X, X_NETWORK_ERROR);
    }
}
