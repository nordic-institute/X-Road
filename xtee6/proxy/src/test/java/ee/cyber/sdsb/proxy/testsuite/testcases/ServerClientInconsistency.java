package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.*;

/**
 * Client sends normal message. Service responds with response that
 * is not consistent with the request.
 * Result: CP responds with ServiceFailed
 */
public class ServerClientInconsistency extends MessageTestCase {
    public ServerClientInconsistency() {
        requestFileName = "xml.query";
        responseFileName = "getstate.query";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_CLIENTPROXY_X, X_SERVICE_FAILED_X,
                X_INCONSISTENT_RESPONSE);
    }
}
