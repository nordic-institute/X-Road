package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.*;

/**
 * Client sends message with missing SOAP header.
 * Result: CP responds with Client.*
 */
public class MissingHeader extends MessageTestCase {
    public MissingHeader() {
        requestFileName = "no-header.query";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(CLIENT_X, X_MISSING_HEADER);
    }
}
