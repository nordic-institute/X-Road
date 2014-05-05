package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MonitorAgentMessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.CLIENT_X;
import static ee.cyber.sdsb.common.ErrorCodes.X_MISSING_HEADER_FIELD;

/**
 * Client sends request with faulty SOAP header (missing field).
 * Result: Client.* error.
 */
public class FaultyHeader extends MonitorAgentMessageTestCase {
    public FaultyHeader() {
        requestFileName = "faulty-header.query";

        monitorAgent.expectFailure();
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(CLIENT_X, X_MISSING_HEADER_FIELD);
    }
}
