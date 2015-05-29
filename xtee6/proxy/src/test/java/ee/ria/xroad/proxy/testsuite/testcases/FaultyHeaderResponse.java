package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.*;

/**
 * Client sends normal request, server responds with faulty SOAP header
 * (missing field).
 * Result: SP sends error
 */
public class FaultyHeaderResponse extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public FaultyHeaderResponse() {
        requestFileName = "getstate.query";
        responseFile = "faulty-header.query";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_SERVERPROXY_X, X_SERVICE_FAILED_X,
                X_MISSING_HEADER_FIELD);
    }
}
