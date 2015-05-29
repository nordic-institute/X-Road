package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.CLIENT_X;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_CONTENT_TYPE;

/**
 * Client sends multipart that does not contain SOAP as the first part.
 * Result: Client.* error message.
 */
public class AttachmentNoSoap extends MessageTestCase {

    /**
     * Constructs the test case.
     */
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
