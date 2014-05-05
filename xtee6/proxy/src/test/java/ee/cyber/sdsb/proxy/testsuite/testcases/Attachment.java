package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

/**
 * Client sends message with attachment. Server responds with normal message.
 * Result: all OK.
 */
public class Attachment extends MessageTestCase {
    public Attachment() {
        requestFileName = "attachm.query";
        requestContentType = "multipart/related; charset=UTF-8; "
                + "boundary=jetty771207119h3h10dty";

        responseFileName = "attachm.answer";
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse) {
        // Not expecting anything in particular.
    }
}
