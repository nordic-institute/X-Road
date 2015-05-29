package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.SERVER_CLIENTPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.X_MISSING_SIGNATURE;

/**
 * Client sends normal request. We emulate server proxy and send multipart
 * that does not contain signature.
 * Result: CP sends error.
 */
public class UnsignedMessageFromServerProxy extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public UnsignedMessageFromServerProxy() {
        requestFileName = "getstate.query";

        responseFile = "no-signature.query";
        responseContentType = "multipart/mixed; charset=UTF-8; "
                + "boundary=jetty42534330h7vzfqv2";
    }

    @Override
    public String getProviderAddress(String providerName) {
        return "127.0.0.2";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_CLIENTPROXY_X, X_MISSING_SIGNATURE);
    }
}
