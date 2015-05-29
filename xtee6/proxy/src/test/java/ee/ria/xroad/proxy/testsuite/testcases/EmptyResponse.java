package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.*;

/**
 * Client sends normal query. Service responds with empty response
 * (content-type is text/xml).
 * Result: Error from SP.
 */
public class EmptyResponse extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public EmptyResponse() {
        requestFileName = "getstate.query";
        responseFile = "empty.query";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_SERVERPROXY_X, X_SERVICE_FAILED_X,
                X_INVALID_SOAP);
    }
}
