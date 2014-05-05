package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.*;

/**
 * Client sends normal request, server responds with invalid content type.
 * Result: SP sends error
 */
public class FaultyResponseContentType extends MessageTestCase {
    public FaultyResponseContentType() {
        requestFileName = "getstate.query";

        responseFileName = "getstate.answer";
        responseContentType = "blah";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_SERVERPROXY_X, X_SERVICE_FAILED_X,
                X_INVALID_CONTENT_TYPE);
    }
}
