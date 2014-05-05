package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.*;

/**
 * Client sends message with invalid SOAP body (no child elements)
 * Result: CP responds with Client.*
 */
public class MalformedBody2 extends MessageTestCase {
    public MalformedBody2() {
        requestFileName = "malformed-body2.query";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(CLIENT_X, X_INVALID_BODY);
    }
}
