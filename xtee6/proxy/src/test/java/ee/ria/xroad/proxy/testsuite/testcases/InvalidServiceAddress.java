package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.*;

/**
 * Client sends normal request. The SP will connect to to nonexisting
 * service and get error.
 * Result: Error from SP
 */
public class InvalidServiceAddress extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public InvalidServiceAddress() {
        requestFileName = "getstate.query";
    }

    @Override
    public String getServiceAddress(ServiceId service) {
        return "http://non.existing.site.com.pom/service";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_SERVERPROXY_X, X_SERVICE_FAILED_X,
                X_NETWORK_ERROR);
    }
}
