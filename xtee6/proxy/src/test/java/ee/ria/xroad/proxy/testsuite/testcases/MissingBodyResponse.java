package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.*;

/**
 * Client sends normal message. Service responds with SOAP with missing body.
 * Result: SP responds with ServiceFailed.
 */
public class MissingBodyResponse extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public MissingBodyResponse() {
        requestFileName = "getstate.query";
        responseFile = "missing-body.query";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_SERVERPROXY_X, X_SERVICE_FAILED_X,
                X_MISSING_BODY);
    }
}
