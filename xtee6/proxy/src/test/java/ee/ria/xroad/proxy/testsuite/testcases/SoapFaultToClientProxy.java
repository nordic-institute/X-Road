package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.CLIENT_X;

/**
 * Send a soap fault to CP.
 */
public class SoapFaultToClientProxy extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public SoapFaultToClientProxy() {
        requestFileName = "fault.query";
        responseFile = "getstate.answer";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(CLIENT_X, "CODE");

    }
}
