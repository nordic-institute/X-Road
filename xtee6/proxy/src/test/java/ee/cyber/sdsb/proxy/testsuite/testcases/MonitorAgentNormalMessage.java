package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MonitorAgentMessageTestCase;

/**
 * The simplest case -- normal message and normal response.
 * Result: client receives message.
 */
public class MonitorAgentNormalMessage extends MonitorAgentMessageTestCase {
    public MonitorAgentNormalMessage() {
        requestFileName = "getstate.query";
        responseFileName = "getstate.answer";

        monitorAgent.expectSuccess();
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse)
            throws Exception {
        // Normal response, nothing more to check here.
    }
}
