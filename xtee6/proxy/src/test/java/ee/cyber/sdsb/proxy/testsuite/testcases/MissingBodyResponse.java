package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.*;

/**
 * Client sends normal message. Service responds with SOAP with missing body.
 * Result: SP responds with ServiceFailed.
 */
public class MissingBodyResponse extends MessageTestCase {
    public MissingBodyResponse() {
        requestFileName = "getstate.query";
        responseFileName = "missing-body.query";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_SERVERPROXY_X, X_SERVICE_FAILED_X,
                X_MISSING_BODY);
    }
}
