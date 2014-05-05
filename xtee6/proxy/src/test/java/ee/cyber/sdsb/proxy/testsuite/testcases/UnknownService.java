package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.common.identifier.ServiceId;
import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static ee.cyber.sdsb.common.ErrorCodes.X_UNKNOWN_SERVICE;

/**
 * The client attempts to make query that it is not allowed to perform.
 * Result: server proxy responds with error message.
 */
public class UnknownService extends MessageTestCase {
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
