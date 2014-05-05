package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.*;

/**
 * Client sends request with invalid content type.
 * Result: Client.* error.
 */
public class FaultyRequestContentType extends MessageTestCase {
    public FaultyRequestContentType() {
        requestFileName = "getstate.query";
        requestContentType = "blah";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(CLIENT_X, X_INVALID_CONTENT_TYPE);
    }
}
