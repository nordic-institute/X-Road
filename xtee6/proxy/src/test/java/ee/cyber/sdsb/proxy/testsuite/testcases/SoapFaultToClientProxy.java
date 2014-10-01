package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.CLIENT_X;

/**
 * Send a soap fault to CP.
 */
public class SoapFaultToClientProxy extends MessageTestCase {
    public SoapFaultToClientProxy() {
        requestFileName = "fault.query";
        responseFileName = "getstate.answer";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(CLIENT_X, "CODE");

    }
}
