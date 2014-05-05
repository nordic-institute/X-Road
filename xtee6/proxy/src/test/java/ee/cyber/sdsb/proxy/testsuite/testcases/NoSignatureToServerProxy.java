package ee.cyber.sdsb.proxy.testsuite.testcases;

import static ee.cyber.sdsb.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static ee.cyber.sdsb.common.ErrorCodes.X_MISSING_SIGNATURE;
import ee.cyber.sdsb.common.PortNumbers;
import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

/**
 * Test connects directly to SP thus impersonating the CP. It sends
 * message without the SOAP.
 * Result: SP responds with error.
 *
 * Note: this test must be redone when we start to use SSL.
 */
public class NoSignatureToServerProxy extends MessageTestCase {
    public NoSignatureToServerProxy() {
        requestFileName = "no-signature.query";
        requestContentType = "multipart/mixed; "
                + "boundary=jetty42534330h7vzfqv2;charset=ISO-8859-1";

        url = "http://127.0.0.1:" + PortNumbers.PROXY_PORT;
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_SERVERPROXY_X, X_MISSING_SIGNATURE);
    }
}
