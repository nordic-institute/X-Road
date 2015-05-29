package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.*;

/**
 * Service responds with empty response (0 bytes) -- not a valid SOAP message.
 * Result: serverproxy encounters parse error and responds with fault.
 */
public class ServerProxyEmptyResponse extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public ServerProxyEmptyResponse() {
        requestFileName = "proxyemulator.query";
        responseFile = "empty.query";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_SERVERPROXY_X, X_SERVICE_FAILED_X,
                X_INVALID_SOAP);
    }
}
