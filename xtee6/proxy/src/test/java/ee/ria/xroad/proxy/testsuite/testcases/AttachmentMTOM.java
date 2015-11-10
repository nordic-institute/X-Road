package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

/**
 * Request message with MTOM content.
 */
public class AttachmentMTOM extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public AttachmentMTOM() {
        requestContentType = "Multipart/Related; "
                + "start-info=\"application/soap+xml\"; "
                + "type=\"application/xop+xml\"; "
                + "boundary=\"jetty771207119h3h10dty\"";
        requestFileName = "attachm-mtom.query";

        responseContentType = "Multipart/Related; "
                + "start-info=\"application/soap+xml\"; "
                + "type=\"application/xop+xml\"; "
                + "boundary=\"jetty771207119h3h10dty\";charset=UTF-8";
        responseFile = "attachm-mtom.answer";
    }

    @Override
    protected void onServiceReceivedRequest(Message receivedRequest) throws Exception {
        super.onServiceReceivedRequest(receivedRequest);

        if (!requestContentType.equals(receivedRequest.getContentType())) {
            throw new RuntimeException(String.format(
                    "Expected request content type '%s' but got '%s'",
                    requestContentType, receivedRequest.getContentType()));
        }

        if (receivedRequest
                .getMultipartHeaders().get(1).get("Content-ID") == null) {
            throw new RuntimeException(
                    "Content-ID header missing in SOAP part");
        }

        if (!("application/xop+xml; type=\"application/soap+xml\"; "
                + "charset=utf-8").equals(receivedRequest
                .getMultipartHeaders().get(1).get("content-type"))) {
            throw new RuntimeException("Unexpected SOAP content type");
        }

        if (receivedRequest
                .getMultipartHeaders().get(2).get("Content-ID") == null) {
            throw new RuntimeException(
                    "Content-ID header missing in attachment part");
        }

        if (!"text/plain; charset=UTF-8; name=attachment.txt".equals(
                receivedRequest.getMultipartHeaders().get(2).get("content-type"))) {
            throw new RuntimeException("Unexpected attachment content type");
        }
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse)
            throws Exception {
        if (!responseContentType.equals(receivedResponse.getContentType())) {
            throw new RuntimeException(String.format(
                    "Expected response content type '%s' but got '%s'",
                    responseContentType, receivedResponse.getContentType()));
        }
    }
}
