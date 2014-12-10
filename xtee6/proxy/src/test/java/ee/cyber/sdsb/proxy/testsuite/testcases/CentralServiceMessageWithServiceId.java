package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

import static ee.cyber.sdsb.common.ErrorCodes.CLIENT_X;
import static ee.cyber.sdsb.common.ErrorCodes.X_DUPLICATE_HEADER_FIELD;

/**
 * Sends message with central service id and service id. Expects error.
 */
public class CentralServiceMessageWithServiceId extends MessageTestCase {

    public CentralServiceMessageWithServiceId() {
        requestFileName = "centralservice-serviceid.query";
        responseFileName = "simple-centralservice.answer";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(CLIENT_X, X_DUPLICATE_HEADER_FIELD);
    }

}
