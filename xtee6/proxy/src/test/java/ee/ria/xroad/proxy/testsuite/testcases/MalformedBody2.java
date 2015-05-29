package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.CLIENT_X;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_BODY;

/**
 * Client sends message with invalid SOAP body (no child elements)
 * Result: CP responds with Client.*
 */
public class MalformedBody2 extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public MalformedBody2() {
        requestFileName = "malformed-body2.query";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(CLIENT_X, X_INVALID_BODY);
    }
}
