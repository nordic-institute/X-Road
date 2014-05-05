package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.common.PortNumbers;
import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.*;

/**
 * Test connects directly to SP thus impersonating the CP. It sends
 * message with invalid content type (text/xml instead of the required
 * multipart/mixed).
 * Result: SP responds with error.
 *
 * Note: this test must be redone when we start to use SSL.
 */
public class InvalidContentTypeFromClientProxy extends MessageTestCase {
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
