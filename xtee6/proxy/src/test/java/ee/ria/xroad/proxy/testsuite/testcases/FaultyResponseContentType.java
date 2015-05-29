package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.*;

/**
 * Client sends normal request, server responds with invalid content type.
 * Result: SP sends error
 */
public class FaultyResponseContentType extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public FaultyResponseContentType() {
        requestFileName = "getstate.query";

        responseFile = "getstate.answer";
        responseContentType = "blah";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_SERVERPROXY_X, X_SERVICE_FAILED_X,
                X_INVALID_CONTENT_TYPE);
    }
}
