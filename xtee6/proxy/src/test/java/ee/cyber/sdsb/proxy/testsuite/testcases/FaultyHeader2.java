package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.*;

/**
 * Client sends request with faulty SOAP header (duplicate field).
 * Result: Client.* error.
 */
public class FaultyHeader2 extends MessageTestCase {
    public FaultyHeader2() {
        requestFileName = "faulty-header2.query";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(CLIENT_X, X_DUPLICATE_HEADER_FIELD);
    }
}
