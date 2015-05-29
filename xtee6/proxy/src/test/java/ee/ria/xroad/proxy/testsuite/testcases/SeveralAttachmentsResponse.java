package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

/**
 * Client sends message with several attachments. Service responds with
 * several attachments.
 * Result: All OK.
 */
public class SeveralAttachmentsResponse extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public SeveralAttachmentsResponse() {
        requestFileName = "attachments.query";
        requestContentType = "multipart/related; charset=UTF-8; "
                + "boundary=jetty771207119h3h10dty";

        responseFile = "attachments.answer";
        responseContentType = "multipart/related; charset=UTF-8; "
                + "boundary=jetty771207119h3h10dty";
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse) {
        //
    }
}
