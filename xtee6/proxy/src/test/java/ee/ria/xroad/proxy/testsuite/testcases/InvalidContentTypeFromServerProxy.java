package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.*;

/**
 * Client sends normal request. Test suite impersonates SP and sends
 * back response with invalid content type (multipart/mixed expected).
 * Result: CP responds with error.
 */
public class InvalidContentTypeFromServerProxy extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public InvalidContentTypeFromServerProxy() {
        requestFileName = "proxyemulator.query";

        responseFile = "getstate.query";
        responseContentType = "oih";
    }

    @Override
    public String getProviderAddress(String providerName) {
        // Turn to dummy server proxy.
        return "127.0.0.2";

    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_CLIENTPROXY_X, X_SERVICE_FAILED_X,
                X_INVALID_CONTENT_TYPE);
    }
}
