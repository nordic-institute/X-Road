package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.*;

/**
 * Client sends normal message, service responds with no-header SOAP.
 * Result: SP responds with ServiceFail
 */
public class MissingHeaderResponse extends MessageTestCase {
    public MissingHeaderResponse() {
        requestFileName = "getstate.query";
        responseFileName = "no-header.query";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_SERVERPROXY_X, X_SERVICE_FAILED_X,
                X_MISSING_HEADER);
    }
}
