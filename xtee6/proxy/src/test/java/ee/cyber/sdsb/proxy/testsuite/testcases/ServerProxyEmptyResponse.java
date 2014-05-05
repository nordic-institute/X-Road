package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static ee.cyber.sdsb.common.ErrorCodes.X_INVALID_SOAP;
import static ee.cyber.sdsb.common.ErrorCodes.X_SERVICE_FAILED_X;

/**
 * Service responds with empty response (0 bytes) -- not a valid SOAP message.
 * Result: serverproxy encounters parse error and responds with fault.
 */
public class ServerProxyEmptyResponse extends MessageTestCase {
    public ServerProxyEmptyResponse() {
        requestFileName = "proxyemulator.query";
        responseFileName = "empty.query";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_SERVERPROXY_X, X_SERVICE_FAILED_X,
                X_INVALID_SOAP);
    }
}
