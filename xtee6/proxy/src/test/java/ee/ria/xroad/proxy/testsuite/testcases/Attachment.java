package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

/**
 * Client sends message with attachment. Server responds with normal message.
 * Result: all OK.
 */
public class Attachment extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public Attachment() {
        requestFileName = "attachm.query";
        requestContentType = "multipart/related; charset=UTF-8; "
                + "boundary=jetty771207119h3h10dty";

        responseFile = "attachm.answer";
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse) {
        // Not expecting anything in particular.
    }
}
