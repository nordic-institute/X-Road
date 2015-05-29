package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MonitorAgentMessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.CLIENT_X;
import static ee.ria.xroad.common.ErrorCodes.X_MISSING_HEADER_FIELD;

/**
 * Client sends request with faulty SOAP header (missing field).
 * Result: Client.* error.
 */
public class FaultyHeader extends MonitorAgentMessageTestCase {

    /**
     * Constructs the test case.
     */
    public FaultyHeader() {
        requestFileName = "faulty-header.query";

        monitorAgent.expectFailure();
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(CLIENT_X, X_MISSING_HEADER_FIELD);
    }
}
