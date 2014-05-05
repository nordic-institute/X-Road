package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.ServiceId;
import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static ee.cyber.sdsb.common.ErrorCodes.X_ACCESS_DENIED;

/**
 * The client attempts to make query that it is not allowed to perform.
 * Result: server proxy responds with error message.
 */
public class NotAllowed extends MessageTestCase {
    public NotAllowed() {
        requestFileName = "notAllowed.query";
    }

    @Override
    public boolean isQueryAllowed(ClientId sender, ServiceId serviceName) {
        return false;
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_SERVERPROXY_X, X_ACCESS_DENIED);
    }
}
