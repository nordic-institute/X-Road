package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

/**
 * Client sends message with attachment. Server responds with normal message.
 * Result: all OK.
 */
public class Attachment extends MessageTestCase {

    private static final String CLIENT_HEADER_NAME = "Client-Header";
    private static final String CLIENT_HEADER_VALUE = "FooBar";

    /**
     * Constructs the test case.
     */
    public Attachment() {
        requestFileName = "attachm.query";
        requestContentType = "multipart/related; charset=UTF-8; "
                + "boundary=jetty771207119h3h10dty";

        responseFile = "attachm.answer";
    }

    @Override
    protected void onReceiveRequest(Message receivedRequest) throws Exception {
        super.onReceiveRequest(receivedRequest);

        String clientHeaderValue =
                receivedRequest.getMultipartHeaders().get(2).get(
                        CLIENT_HEADER_NAME);

        if (!CLIENT_HEADER_VALUE.equals(clientHeaderValue)) {
            throw new RuntimeException(String.format(
                    "Expected client header '%s' with value '%s', but got '%s'",
                    CLIENT_HEADER_NAME, CLIENT_HEADER_VALUE,
                    clientHeaderValue));
        }
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse) {
        // Not expecting anything in particular.
    }
}
