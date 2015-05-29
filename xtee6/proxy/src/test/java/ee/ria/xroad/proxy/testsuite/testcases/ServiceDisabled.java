package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.X_SERVICE_DISABLED;

/**
 * The client attempts to make query that it is not allowed to perform.
 * Result: server proxy responds with error message.
 */
public class ServiceDisabled extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public ServiceDisabled() {
        requestFileName = "getstate.query";
    }

    @Override
    public String getDisabledNotice(ServiceId service) {
        return "Service is disabled";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_SERVERPROXY_X, X_SERVICE_DISABLED);
    }
}
