package ee.cyber.sdsb.proxy.testsuite.testcases;

import static ee.cyber.sdsb.common.ErrorCodes.SERVER_CLIENTPROXY_X;
import static ee.cyber.sdsb.common.ErrorCodes.X_INVALID_CONTENT_TYPE;
import static ee.cyber.sdsb.common.ErrorCodes.X_SERVICE_FAILED_X;
import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

/**
 * Client sends normal message. SP responds with multipart that contains
 * invalid attachment (no content-type header).
 * Result: CP responds with error.
 */
public class ServerProxyFaultyAttachment extends MessageTestCase {
    public ServerProxyFaultyAttachment() {
        requestFileName = "getstate.query";

        responseFileName = "attachm-error.query";
        responseContentType = "multipart/mixed; "
                + "charset=UTF-8; boundary=jetty771207119h3h10dty";

    }

    @Override
    public String getProviderAddress(String providerName) {
        return "127.0.0.2";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_CLIENTPROXY_X, X_SERVICE_FAILED_X,
                X_INVALID_CONTENT_TYPE);
    }
}
