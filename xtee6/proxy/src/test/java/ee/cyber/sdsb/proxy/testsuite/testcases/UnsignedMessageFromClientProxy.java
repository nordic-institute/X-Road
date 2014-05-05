package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.common.PortNumbers;
import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.*;

/**
 * Test impersonates client proxy and connects directly to SP. Sends
 * message that does not contain signature.
 * Result: SP responds with error
 */
public class UnsignedMessageFromClientProxy extends MessageTestCase {
    public UnsignedMessageFromClientProxy() {
        requestFileName = "attachm.query";
        requestContentType = "multipart/mixed; charset=UTF-8; "
                + "boundary=jetty771207119h3h10dty";

        // Connect directly to serverproxy
        url = "http://127.0.0.1:" + PortNumbers.PROXY_PORT;
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_SERVERPROXY_X, X_MISSING_SIGNATURE);
    }
}
