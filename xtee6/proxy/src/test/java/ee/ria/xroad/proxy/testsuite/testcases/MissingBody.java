package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.CLIENT_X;
import static ee.ria.xroad.common.ErrorCodes.X_MISSING_BODY;

/**
 * Client makes a query that does not contain SOAP body.
 * Result: Client.* error message from client proxy indicating that
 * problem is on the client's side.
 */
public class MissingBody extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public MissingBody() {
        requestFileName = "missing-body.query";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(CLIENT_X, X_MISSING_BODY);
    }
}
