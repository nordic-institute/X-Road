package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.*;

/**
 * Client sends normal request. Service responds with SOAP with invalid body.
 * Result: SP responds with ServiceFailed error
 */
public class MalformedBodyResponse extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public MalformedBodyResponse() {
        requestFileName = "getstate.query";
        responseFile = "malformed-body2.query";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_SERVERPROXY_X, X_SERVICE_FAILED_X,
                X_INVALID_BODY);
    }
}
