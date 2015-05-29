package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.*;

/**
 * Client sends normal message, service responds with invalid attachment.
 * Result: SP responds with ServiceFailed.*
 */
public class ServerProxyAttachmentError extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public ServerProxyAttachmentError() {
        requestFileName = "getstate.query";

        responseFile = "attachm-error.query";
        responseContentType = "multipart/related; charset=UTF-8; "
                + "boundary=jetty771207119h3h10dty";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_SERVERPROXY_X, X_SERVICE_FAILED_X,
                X_INVALID_CONTENT_TYPE);
    }
}
