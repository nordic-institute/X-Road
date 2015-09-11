package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

/**
 * Normal message and normal response, both without optional userId header field.
 * Result: client receives message.
 */
public class MissingUserIdHeader extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public MissingUserIdHeader() {
        requestFileName = "missing-userId.query";
        responseFile = "missing-userId.answer";
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse)
            throws Exception {
        // Normal response, nothing more to check here.
    }
}
