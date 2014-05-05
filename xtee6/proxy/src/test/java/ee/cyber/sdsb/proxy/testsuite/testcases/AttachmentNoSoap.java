package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.*;

/**
 * Client sends multipart that does not contain SOAP as the first part.
 * Result: Client.* error message.
 */
public class AttachmentNoSoap extends MessageTestCase {
    public AttachmentNoSoap() {
        requestFileName = "attachm-no-soap.query";
        requestContentType = "multipart/related; charset=UTF-8; "
                + "boundary=jetty771207119h3h10dty";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(CLIENT_X, X_INVALID_CONTENT_TYPE);
    }
}
