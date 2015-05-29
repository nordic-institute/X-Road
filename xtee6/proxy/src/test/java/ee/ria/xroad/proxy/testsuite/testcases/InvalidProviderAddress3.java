package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.SERVER_CLIENTPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.X_UNKNOWN_MEMBER;

/**
 * Client proxy attempts to make query to non-existing server proxy address
 * and fails.
 * Result: clientproxy sends error message.
 */
public class InvalidProviderAddress3 extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public InvalidProviderAddress3() {
        requestFileName = "attachm.query";
        requestContentType = "multipart/related; charset=UTF-8; "
                + "boundary=jetty771207119h3h10dty";
    }

    @Override
    public String getProviderAddress(String providerName) {
        return null;
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_CLIENTPROXY_X, X_UNKNOWN_MEMBER);
    }
}
