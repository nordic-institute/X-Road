package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.*;

/**
 * Client uses GET instead of POST.
 * Result: CP responds with Client.*
 */
public class WrongHttpMethod extends MessageTestCase {
    public WrongHttpMethod() {
        requestFileName = "getstate.query";
        httpMethod = "GET";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(CLIENT_X, X_INVALID_HTTP_METHOD);
    }
}
