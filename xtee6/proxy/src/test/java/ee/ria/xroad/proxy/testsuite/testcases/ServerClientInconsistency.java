package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.*;

/**
 * Client sends normal message. Service responds with response that
 * is not consistent with the request.
 * Result: CP responds with ServiceFailed
 */
public class ServerClientInconsistency extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public ServerClientInconsistency() {
        requestFileName = "xml.query";
        responseFile = "getstate.query";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_CLIENTPROXY_X, X_SERVICE_FAILED_X,
                X_INCONSISTENT_RESPONSE);
    }
}
