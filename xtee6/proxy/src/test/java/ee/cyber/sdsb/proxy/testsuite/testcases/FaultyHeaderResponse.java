package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.*;

/**
 * Client sends normal request, server responds with faulty SOAP header
 * (missing field).
 * Result: SP sends error
 */
public class FaultyHeaderResponse extends MessageTestCase {
    public FaultyHeaderResponse() {
        requestFileName = "getstate.query";
        responseFileName = "faulty-header.query";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_SERVERPROXY_X, X_SERVICE_FAILED_X,
                X_MISSING_HEADER_FIELD);
    }
}
