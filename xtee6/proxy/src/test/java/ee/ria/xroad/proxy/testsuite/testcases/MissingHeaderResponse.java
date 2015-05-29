package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.*;

/**
 * Client sends normal message, service responds with no-header SOAP.
 * Result: SP responds with ServiceFail
 */
public class MissingHeaderResponse extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public MissingHeaderResponse() {
        requestFileName = "getstate.query";
        responseFile = "no-header.query";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_SERVERPROXY_X, X_SERVICE_FAILED_X,
                X_MISSING_HEADER);
    }
}
