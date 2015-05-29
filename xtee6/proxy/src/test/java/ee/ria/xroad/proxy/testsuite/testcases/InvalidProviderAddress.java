package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.SERVER_CLIENTPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.X_NETWORK_ERROR;

/**
 * Client proxy attempts to make query to non-existing server proxy address
 * and fails.
 * Result: clientproxy sends error message.
 */
public class InvalidProviderAddress extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public InvalidProviderAddress() {
        requestFileName = "invalid-provider-address.query";
    }

    @Override
    public String getProviderAddress(String providerName) {
        return "127.0.0.7";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_CLIENTPROXY_X, X_NETWORK_ERROR);
    }
}
