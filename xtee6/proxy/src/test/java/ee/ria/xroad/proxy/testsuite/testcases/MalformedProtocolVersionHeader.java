package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.CLIENT_X;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_PROTOCOL_VERSION;

/**
 * Message with malformed protocolVersion header.
 * Result: client receives error.
 */
public class MalformedProtocolVersionHeader extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public MalformedProtocolVersionHeader() {
        requestFileName = "malformed-protocolVersion.query";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(CLIENT_X, X_INVALID_PROTOCOL_VERSION);
    }
}
