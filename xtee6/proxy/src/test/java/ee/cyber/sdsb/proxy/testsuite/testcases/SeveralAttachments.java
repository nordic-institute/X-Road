package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

/**
 * Client sends message with several attachments. Service sends usual response.
 * Result: All OK.
 */
public class SeveralAttachments extends MessageTestCase {
    public SeveralAttachments() {
        requestFileName = "attachments.query";
        requestContentType = "multipart/related; charset=UTF-8; "
                + "boundary=jetty771207119h3h10dty";
        responseFileName = "attachm.answer";
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse) {
        //
    }
}
