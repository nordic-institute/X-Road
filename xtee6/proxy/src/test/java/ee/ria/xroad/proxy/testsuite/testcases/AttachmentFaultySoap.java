package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.CLIENT_X;
import static ee.ria.xroad.common.ErrorCodes.X_MISSING_HEADER_FIELD;


/**
 * Client sends message with multipart that contains invalid SOAP.
 * Result: Client.* error message.
 */
public class AttachmentFaultySoap extends MessageTestCase {

    /**
     * Constructs the test case.
     */
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
