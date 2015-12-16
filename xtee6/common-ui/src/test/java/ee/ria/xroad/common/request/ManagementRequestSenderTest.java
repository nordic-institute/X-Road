package ee.ria.xroad.common.request;

import org.junit.Test;

import ee.ria.xroad.common.message.SoapMessageImpl;

import static ee.ria.xroad.common.message.SoapMessageTestUtil.createResponse;
import static junit.framework.Assert.assertEquals;

/**
 * Testing static methods of ManagementRequestSender
 */
public class ManagementRequestSenderTest {

    /**
     * Tests whether getting request id works as intended.
     * @throws Exception in case of unexpected errors
     */
    @Test
    public void getRequestIdFromManagementServiceResponse() throws Exception {
        SoapMessageImpl response =
                createResponse("response-with-requestId.answer");

        Integer requestId = ManagementRequestSender.getRequestId(response);

        Integer expectedRequestId = 413;
        assertEquals(expectedRequestId, requestId);
    }

}
