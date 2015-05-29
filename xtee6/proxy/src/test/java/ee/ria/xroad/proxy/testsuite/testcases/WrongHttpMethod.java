package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.CLIENT_X;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_HTTP_METHOD;

/**
 * Client uses GET instead of POST.
 * Result: CP responds with Client.*
 */
public class WrongHttpMethod extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public WrongHttpMethod() {
        requestFileName = "getstate.query";
        httpMethod = "GET";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(CLIENT_X, X_INVALID_HTTP_METHOD);
    }
}
