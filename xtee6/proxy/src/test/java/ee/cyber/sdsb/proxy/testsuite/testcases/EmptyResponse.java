package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.*;

/**
 * Client sends normal query. Service responds with empty response
 * (content-type is text/xml).
 * Result: Error from SP.
 */
public class EmptyResponse extends MessageTestCase {
    public EmptyResponse() {
        requestFileName = "getstate.query";
        responseFileName = "empty.query";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_SERVERPROXY_X, X_SERVICE_FAILED_X,
                X_INVALID_SOAP);
    }
}
