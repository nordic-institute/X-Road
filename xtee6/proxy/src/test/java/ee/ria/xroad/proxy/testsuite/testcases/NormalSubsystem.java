package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

/**
 * The simplest case -- normal message and normal response.
 * Result: client receives message.
 */
public class NormalSubsystem extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public NormalSubsystem() {
        requestFileName = "getstate-subsystem.query";
        responseFile = "getstate-subsystem.answer";
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse)
            throws Exception {
        // Normal response, nothing more to check here.
    }
}
