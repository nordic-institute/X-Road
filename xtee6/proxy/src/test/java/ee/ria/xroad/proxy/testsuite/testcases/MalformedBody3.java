package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.CLIENT_X;
import static ee.ria.xroad.common.ErrorCodes.X_INCONSISTENT_HEADERS;

/**
 * Client sends message with invalid SOAP body (service name is
 * different from headers)
 * Result: CP responds with Client.*
 */
public class MalformedBody3 extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public MalformedBody3() {
        requestFileName = "malformed-body3.query";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(CLIENT_X, X_INCONSISTENT_HEADERS);
    }
}
