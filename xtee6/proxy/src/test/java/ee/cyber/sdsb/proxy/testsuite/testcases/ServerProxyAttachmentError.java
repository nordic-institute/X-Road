package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.*;

/**
 * Client sends normal message, service responds with invalid attachment.
 * Result: SP responds with ServiceFailed.*
 */
public class ServerProxyAttachmentError extends MessageTestCase {
    public ServerProxyAttachmentError() {
        requestFileName = "getstate.query";

        responseFileName = "attachm-error.query";
        responseContentType = "multipart/related; charset=UTF-8; "
                + "boundary=jetty771207119h3h10dty";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_SERVERPROXY_X, X_SERVICE_FAILED_X,
                X_INVALID_CONTENT_TYPE);
    }
}
