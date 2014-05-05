package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

/**
 * Client sends message with attachment. Server responds with attachment.
 * Result: all OK.
 */
public class Attachment2 extends MessageTestCase {
    public Attachment2() {
        requestFileName = "attachm2.query";
        requestContentType = "multipart/related; "
                + "boundary=jetty771207119h3h10dty";

        responseFileName = "attachm2.answer";
        responseContentType = "multipart/related; charset=UTF-8; "
                + "boundary=jetty771207119h3h10dty";
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse) {
    }
}
