package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.common.PortNumbers;
import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_HTTP_METHOD;

/**
 * Connect directly to SP, impersonating CP. Send HTTP GET request.
 * Result: SP responds with error.
 */
public class WrongHttpMethodServerProxy extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public WrongHttpMethodServerProxy() {
        requestFileName = "getstate.query";
        httpMethod = "GET";
        url = "http://127.0.0.1:" + PortNumbers.PROXY_PORT;
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_SERVERPROXY_X, X_INVALID_HTTP_METHOD);
    }
}
