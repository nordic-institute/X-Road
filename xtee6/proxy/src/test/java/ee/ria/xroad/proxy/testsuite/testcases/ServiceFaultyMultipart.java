package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.*;

/**
 * Client sends normal message, service responds with invalid multipart.
 * Result: SP responds with ServiceFailed.
 */
public class ServiceFaultyMultipart extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public ServiceFaultyMultipart() {
        requestFileName = "getstate.query";

        responseFile = "faulty-multipart.query";
        responseContentType = "multipart/related; "
                + "boundary=jetty771207119h3h10dty; charset=utf-8";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_SERVERPROXY_X, X_SERVICE_FAILED_X,
                X_INVALID_MESSAGE);
    }
}
