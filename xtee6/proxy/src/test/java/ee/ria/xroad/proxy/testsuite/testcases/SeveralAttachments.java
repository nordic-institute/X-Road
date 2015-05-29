package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

/**
 * Client sends message with several attachments. Service sends usual response.
 * Result: All OK.
 */
public class SeveralAttachments extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public SeveralAttachments() {
        requestFileName = "attachments.query";
        requestContentType = "multipart/related; charset=UTF-8; "
                + "boundary=jetty771207119h3h10dty";
        responseFile = "attachm.answer";
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse) {
        //
    }
}
