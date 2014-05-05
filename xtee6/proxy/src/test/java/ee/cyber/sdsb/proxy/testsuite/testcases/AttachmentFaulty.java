package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.CLIENT_X;
import static ee.cyber.sdsb.common.ErrorCodes.X_INVALID_CONTENT_TYPE;

/**
 * Client sends multipart with one part that does not have
 * content-type header.
 * Result: Server responds with Client.* error message.
 */
public class AttachmentFaulty extends MessageTestCase {
    public AttachmentFaulty() {
        requestFileName = "attachm-error.query";
        requestContentType = "multipart/related; charset=UTF-8; "
                + "boundary=jetty771207119h3h10dty";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(CLIENT_X, X_INVALID_CONTENT_TYPE);
    }
}
