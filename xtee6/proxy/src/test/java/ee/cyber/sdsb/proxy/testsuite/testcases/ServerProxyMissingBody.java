package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.*;

/**
 * Client sends normal message, SP emulator responds with SOAP response
 * with missing body.
 * Result: CP responds with error.
 */
public class ServerProxyMissingBody extends MessageTestCase {
    public ServerProxyMissingBody() {
        requestFileName = "getstate.query";

        responseFileName = "missing-body.query";
        responseContentType = "text/xml; boundary=jetty771207119h3h10dty";
    }

    @Override
    public String getProviderAddress(String providerName) {
        return "127.0.0.2";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_CLIENTPROXY_X, X_SERVICE_FAILED_X,
                X_MISSING_BODY);
    }
}
