package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.CLIENT_X;
import static ee.ria.xroad.common.ErrorCodes.X_MISSING_HEADER;

/**
 * Client sends message with missing SOAP header.
 * Result: CP responds with Client.*
 */
public class MissingHeader extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public MissingHeader() {
        requestFileName = "no-header.query";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(CLIENT_X, X_MISSING_HEADER);
    }
}
