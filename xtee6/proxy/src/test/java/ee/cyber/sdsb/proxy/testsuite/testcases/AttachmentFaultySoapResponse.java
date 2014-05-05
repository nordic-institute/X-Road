package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.*;

/**
 * Client sends normal message. Server responds with multipart that
 * has invalid SOAP in it.
 * Result: error.
 */
public class AttachmentFaultySoapResponse extends MessageTestCase {
    public AttachmentFaultySoapResponse() {
        requestFileName = "getstate.query";

        responseFileName = "attachm-faulty-soap.query";
        responseContentType = "multipart/related; charset=UTF-8; "
                + "boundary=jetty771207119h3h10dty";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_SERVERPROXY_X, X_SERVICE_FAILED_X,
                X_MISSING_HEADER_FIELD);
    }
}
