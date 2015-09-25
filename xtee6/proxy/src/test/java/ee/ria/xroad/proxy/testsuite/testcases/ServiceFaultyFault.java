package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

/**
 * Client sends normal message, service responds with invalid soap faul.
 * Result: .
 */
public class ServiceFaultyFault extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public ServiceFaultyFault() {
        requestFileName = "getstate.query";

        responseFile = "faulty-fault.answer";
        responseContentType = "text/xml";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode("soap:Server");
    }
}
