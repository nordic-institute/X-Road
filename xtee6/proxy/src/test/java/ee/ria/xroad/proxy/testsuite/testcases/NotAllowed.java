package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.X_ACCESS_DENIED;

/**
 * The client attempts to make query that it is not allowed to perform.
 * Result: server proxy responds with error message.
 */
public class NotAllowed extends MessageTestCase {

    /**
     * Constructs the test case.
     */
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
