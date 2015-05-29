package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.CLIENT_X;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_BODY;

/**
 * Client sends message with invalid SOAP body (duplicate Body element)
 * Result: CP responds with Client.*
 */
public class MalformedBody1 extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public MalformedBody1() {
        requestFileName = "malformed-body1.query";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(CLIENT_X, X_INVALID_BODY);
    }
}
