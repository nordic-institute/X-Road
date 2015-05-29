package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.CLIENT_X;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_CONTENT_TYPE;

/**
 * Client sends request with invalid content type.
 * Result: Client.* error.
 */
public class FaultyRequestContentType extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public FaultyRequestContentType() {
        requestFileName = "getstate.query";
        requestContentType = "blah";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(CLIENT_X, X_INVALID_CONTENT_TYPE);
    }
}
