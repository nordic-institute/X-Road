package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.*;

/**
 * Client sends normal message. The CP gets connection refused error
 * when connecting to SP.
 * Result: CP responds with error
 */
public class ServerProxyConnectionRefused extends MessageTestCase {
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
