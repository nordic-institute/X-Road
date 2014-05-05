package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.common.identifier.ServiceId;
import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.*;

/**
 * Client sends request with attachments. The SP will connect to to nonexisting
 * service and get error.
 * Result: Error from SP
 */
public class InvalidServiceAddress2 extends MessageTestCase {
    public InvalidServiceAddress2() {
        requestFileName = "attachm.query";
        requestContentType = "multipart/related; charset=UTF-8; "
                + "boundary=jetty771207119h3h10dty";

        responseFileName = "attachm.answer";
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
