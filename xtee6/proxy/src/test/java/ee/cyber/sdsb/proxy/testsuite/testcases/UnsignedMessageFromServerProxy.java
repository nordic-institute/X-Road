package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.common.PortNumbers;
import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.*;

/**
 * Client sends normal request. We emulate server proxy and send multipart
 * that does not contain signature.
 * Result: CP sends error.
 */
public class UnsignedMessageFromServerProxy extends MessageTestCase {
    public UnsignedMessageFromServerProxy() {
        requestFileName = "getstate.query";

        responseFileName = "no-signature.query";
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
