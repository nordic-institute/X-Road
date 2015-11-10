package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

/**
 * MIME message with only SOAP part.
 */
public class MimeSoap extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public MimeSoap() {
        requestFileName = "mimesoap.query";
        requestContentType = "multipart/related; charset=UTF-8; "
                + "boundary=jetty771207119h3h10dty";

        responseFile = "mimesoap.answer";
        responseContentType = "multipart/related; charset=UTF-8; "
                + "boundary=jetty771207119h3h10dty";
    }

    @Override
    protected void onServiceReceivedRequest(Message receivedRequest)
            throws Exception {
        super.onServiceReceivedRequest(receivedRequest);

        if (!requestContentType.equalsIgnoreCase(
                receivedRequest.getContentType())) {
            throw new Exception(String.format("Unexpected content type. "
                    + "Expected '%s', but was '%s'",
                    requestContentType,
                    receivedRequest.getContentType()));
        }

        if (receivedRequest.getMultipartHeaders().size() != 2) {
            throw new Exception("Unexpected number of parts in message.");
        }
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse)
            throws Exception {
        // Normal response, nothing more to check here.
    }
}
