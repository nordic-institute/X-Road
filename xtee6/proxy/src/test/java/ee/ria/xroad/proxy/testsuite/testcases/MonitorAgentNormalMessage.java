package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MonitorAgentMessageTestCase;

/**
 * The simplest case -- normal message and normal response.
 * Result: client receives message.
 */
public class MonitorAgentNormalMessage extends MonitorAgentMessageTestCase {

    /**
     * Constructs the test case.
     */
    public MonitorAgentNormalMessage() {
        requestFileName = "getstate.query";
        responseFile = "getstate.answer";

        monitorAgent.expectSuccess();
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse)
            throws Exception {
        // Normal response, nothing more to check here.
    }
}
