package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.*;

/**
 * Client proxy attempts to make query to non-existing server proxy address
 * and fails.
 * Result: clientproxy sends error message.
 */
public class InvalidProviderAddress2 extends MessageTestCase {
    public InvalidProviderAddress2() {
        requestFileName = "attachm.query";
        requestContentType = "multipart/related; charset=UTF-8; "
                + "boundary=jetty771207119h3h10dty";
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
