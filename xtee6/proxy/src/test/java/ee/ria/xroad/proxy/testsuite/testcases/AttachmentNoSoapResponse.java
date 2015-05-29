package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.*;

/**
 * Client makes normal request, service responds with multipart that contains
 * no SOAP.
 * Result: error
 */
public class AttachmentNoSoapResponse extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public AttachmentNoSoapResponse() {
        requestFileName = "getstate.query";

        responseFile = "attachm-no-soap.query";
        responseContentType = "multipart/related; charset=UTF-8; "
                + "boundary=jetty771207119h3h10dty";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_SERVERPROXY_X, X_SERVICE_FAILED_X,
                X_INVALID_CONTENT_TYPE);
    }
}
