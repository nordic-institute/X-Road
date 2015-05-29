package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.*;

/**
 * Client sends normal message. SP responds with multipart that contains
 * invalid attachment (no content-type header).
 * Result: CP responds with error.
 */
public class ServerProxyFaultyAttachment extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public ServerProxyFaultyAttachment() {
        requestFileName = "getstate.query";

        responseFile = "attachm-error.query";
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
