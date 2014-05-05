package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

/**
 * Client sends message with several attachments. Service responds with
 * several attachments.
 * Result: All OK.
 */
public class SeveralAttachmentsResponse extends MessageTestCase {
    public SeveralAttachmentsResponse() {
        requestFileName = "attachments.query";
        requestContentType = "multipart/related; charset=UTF-8; "
                + "boundary=jetty771207119h3h10dty";

        responseFileName = "attachments.answer";
        responseContentType = "multipart/related; charset=UTF-8; "
                + "boundary=jetty771207119h3h10dty";
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse) {
        //
    }
}
