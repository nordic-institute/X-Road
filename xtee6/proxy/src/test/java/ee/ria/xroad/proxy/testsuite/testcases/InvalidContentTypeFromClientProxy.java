package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.common.PortNumbers;
import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.*;

/**
 * Test connects directly to SP thus impersonating the CP. It sends
 * message with invalid content type (text/xml instead of the required
 * multipart/mixed).
 * Result: SP responds with error.
 *
 * Note: this test must be redone when we start to use SSL.
 */
public class InvalidContentTypeFromClientProxy extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public InvalidContentTypeFromClientProxy() {
        requestFileName = "getstate.query";

        url = "http://127.0.0.1:" + PortNumbers.PROXY_PORT;
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_SERVERPROXY_X, X_SERVICE_FAILED_X,
                X_INVALID_CONTENT_TYPE);
    }
}
