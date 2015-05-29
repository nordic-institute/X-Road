package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.*;

/**
 * Client sends request with attachments. The SP will connect to to nonexisting
 * service and get error.
 * Result: Error from SP
 */
public class InvalidServiceAddress2 extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public InvalidServiceAddress2() {
        requestFileName = "attachm.query";
        requestContentType = "multipart/related; charset=UTF-8; "
                + "boundary=jetty771207119h3h10dty";

        responseFile = "attachm.answer";
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
