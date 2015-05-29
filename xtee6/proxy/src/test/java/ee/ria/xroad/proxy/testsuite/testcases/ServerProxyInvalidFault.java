package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.*;

/**
 * Client sends normal response. Emulated SP responds with text/xml
 * that is not fault (in protocol, SP response with text/xml content type
 * MUST be fault).
 * Result: CP responds with error.
 */
public class ServerProxyInvalidFault extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public ServerProxyInvalidFault() {
        requestFileName = "getstate.query";

        responseFile = "getstate.query";
        responseContentType = "text/xml; charset=\"UTF-8\"; boundary=foobar";
    }

    @Override
    public String getProviderAddress(String providerName) {
        return "127.0.0.2";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_CLIENTPROXY_X, X_SERVICE_FAILED_X,
                X_INVALID_MESSAGE);
    }
}
