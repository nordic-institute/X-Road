package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.*;

/**
 * Client sends normal message. Server responds with multipart that
 * has invalid SOAP in it.
 * Result: error.
 */
public class AttachmentFaultySoapResponse extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public AttachmentFaultySoapResponse() {
        requestFileName = "getstate.query";

        responseFile = "attachm-faulty-soap.query";
        responseContentType = "multipart/related; charset=UTF-8; "
                + "boundary=jetty771207119h3h10dty";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_SERVERPROXY_X, X_SERVICE_FAILED_X,
                X_MISSING_HEADER_FIELD);
    }
}
