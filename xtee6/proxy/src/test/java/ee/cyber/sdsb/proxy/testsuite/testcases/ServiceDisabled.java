package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.common.identifier.ServiceId;
import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static ee.cyber.sdsb.common.ErrorCodes.X_SERVICE_DISABLED;

/**
 * The client attempts to make query that it is not allowed to perform.
 * Result: server proxy responds with error message.
 */
public class ServiceDisabled extends MessageTestCase {
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
