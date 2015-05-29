package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.*;

/**
 * Client sends normal message. SP responds with fault message
 * (content type is text/xml instead of multipart) that is empty.
 * Result: CP responds with error.
 */
public class ServerProxyEmptyFault extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public ServerProxyEmptyFault() {
        requestFileName = "getstate.query";

        responseFile = "empty.query";
    }

    @Override
    public String getProviderAddress(String providerName) {
        return "127.0.0.2";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_CLIENTPROXY_X, X_SERVICE_FAILED_X,
                X_INVALID_SOAP);
    }
}
