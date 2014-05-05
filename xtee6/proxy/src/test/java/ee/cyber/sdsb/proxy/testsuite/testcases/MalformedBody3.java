package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.*;

/**
 * Client sends message with invalid SOAP body (service name is
 * different from headers)
 * Result: CP responds with Client.*
 */
public class MalformedBody3 extends MessageTestCase {
    public MalformedBody3() {
        requestFileName = "malformed-body3.query";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(CLIENT_X, X_INCONSISTENT_HEADERS);
    }
}
