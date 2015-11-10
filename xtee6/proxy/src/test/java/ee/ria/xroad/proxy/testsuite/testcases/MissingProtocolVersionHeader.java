package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.CLIENT_X;
import static ee.ria.xroad.common.ErrorCodes.X_MISSING_HEADER_FIELD;

/**
 * Message without protocolVersion header.
 * Result: client receives error.
 */
public class MissingProtocolVersionHeader extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public MissingProtocolVersionHeader() {
        requestFileName = "missing-protocolVersion.query";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(CLIENT_X, X_MISSING_HEADER_FIELD);
    }
}
