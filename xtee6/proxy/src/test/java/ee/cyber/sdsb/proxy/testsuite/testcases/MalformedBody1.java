package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.*;

/**
 * Client sends message with invalid SOAP body (duplicate Body element)
 * Result: CP responds with Client.*
 */
public class MalformedBody1 extends MessageTestCase {
    public MalformedBody1() {
        requestFileName = "malformed-body1.query";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(CLIENT_X, X_INVALID_BODY);
    }
}
