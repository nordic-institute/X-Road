package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.*;

/**
 * Client makes a query that does not contain SOAP body.
 * Result: Client.* error message from client proxy indicating that
 * problem is on the client's side.
 */
public class MissingBody extends MessageTestCase {
    public MissingBody() {
        requestFileName = "missing-body.query";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(CLIENT_X, X_MISSING_BODY);
    }
}
