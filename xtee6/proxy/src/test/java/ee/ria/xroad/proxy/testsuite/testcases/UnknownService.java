package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.X_UNKNOWN_SERVICE;

/**
 * The client attempts to make query that it is not allowed to perform.
 * Result: server proxy responds with error message.
 */
public class UnknownService extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public UnknownService() {
        requestFileName = "getstate.query";
    }

    @Override
    public boolean serviceExists(ServiceId serviceName) {
        return false;
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_SERVERPROXY_X, X_UNKNOWN_SERVICE);
    }
}
