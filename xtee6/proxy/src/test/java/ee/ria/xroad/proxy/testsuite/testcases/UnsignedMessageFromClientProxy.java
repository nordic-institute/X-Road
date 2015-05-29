package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.common.PortNumbers;
import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.X_MISSING_SIGNATURE;

/**
 * Test impersonates client proxy and connects directly to SP. Sends
 * message that does not contain signature.
 * Result: SP responds with error
 */
public class UnsignedMessageFromClientProxy extends MessageTestCase {

    /**
     * Constructs the test case.
     */
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
