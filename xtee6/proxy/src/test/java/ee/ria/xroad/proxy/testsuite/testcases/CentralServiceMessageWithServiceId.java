package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import static ee.ria.xroad.common.ErrorCodes.CLIENT_X;
import static ee.ria.xroad.common.ErrorCodes.X_DUPLICATE_HEADER_FIELD;

/**
 * Sends message with central service id and service id. Expects error.
 */
public class CentralServiceMessageWithServiceId extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public CentralServiceMessageWithServiceId() {
        requestFileName = "centralservice-serviceid.query";
        responseFile = "simple-centralservice.answer";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(CLIENT_X, X_DUPLICATE_HEADER_FIELD);
    }

}
