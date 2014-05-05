package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.*;

/**
 * Client sends normal message, service responds with invalid multipart.
 * Result: SP responds with ServiceFailed.
 */
public class ServiceFaultyMultipart extends MessageTestCase {
    public ServiceFaultyMultipart() {
        requestFileName = "getstate.query";

        responseFileName = "faulty-multipart.query";
        responseContentType = "multipart/related; "
                + "boundary=jetty771207119h3h10dty; charset=utf-8";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_SERVERPROXY_X, X_SERVICE_FAILED_X,
                X_INVALID_MESSAGE);
    }
}
