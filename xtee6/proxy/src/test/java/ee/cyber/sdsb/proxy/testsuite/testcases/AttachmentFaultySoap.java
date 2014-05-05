package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.*;


/**
 * Client sends message with multipart that contains invalid SOAP.
 * Result: Client.* error message.
 */
public class AttachmentFaultySoap extends MessageTestCase {
    public AttachmentFaultySoap() {
        requestFileName = "attachm-faulty-soap.query";
        requestContentType = "multipart/related; charset=UTF-8; "
                + "boundary=jetty771207119h3h10dty";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(CLIENT_X, X_MISSING_HEADER_FIELD);
    }
}
