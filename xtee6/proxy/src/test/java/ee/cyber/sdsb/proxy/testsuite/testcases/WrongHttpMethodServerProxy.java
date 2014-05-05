package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.common.PortNumbers;
import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.*;

/**
 * Connect directly to SP, impersonating CP. Send HTTP GET request.
 * Result: SP responds with error.
 */
public class WrongHttpMethodServerProxy extends MessageTestCase {
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
