package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.common.PortNumbers;
import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.X_MISSING_SOAP;

/**
 * We connect directly to SP and send message with multipart/mixed
 * content type. Message doesn't contain multipart.
 * Result: SP responds with error.
 * Note
 */
public class ServerProxyProcessingError extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public ServerProxyProcessingError() {
        requestFileName = "getstate.query";
        requestContentType = "multipart/mixed; boundary=foobar";

        url = "http://127.0.0.1:" + PortNumbers.PROXY_PORT;
    }

    @Override
    public String getProviderAddress(String providerName) {
        return "127.0.0.2";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_SERVERPROXY_X, X_MISSING_SOAP);
    }
}
