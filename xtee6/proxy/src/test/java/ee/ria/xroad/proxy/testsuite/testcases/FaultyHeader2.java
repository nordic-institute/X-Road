package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.CLIENT_X;
import static ee.ria.xroad.common.ErrorCodes.X_DUPLICATE_HEADER_FIELD;

/**
 * Client sends request with faulty SOAP header (duplicate field).
 * Result: Client.* error.
 */
public class FaultyHeader2 extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public FaultyHeader2() {
        requestFileName = "faulty-header2.query";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(CLIENT_X, X_DUPLICATE_HEADER_FIELD);
    }
}
