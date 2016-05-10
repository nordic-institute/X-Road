package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

/**
 * Client sends message with attachment. Server responds with attachment. Both
 * messages have the UTF-8 BOM bytes.
 * Result: all OK.
 */
public class Utf8BomAttachment2 extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public Utf8BomAttachment2() {
        requestFileName = "attachm2.query";
        requestContentType = "multipart/related; "
                + "boundary=jetty771207119h3h10dty";

        responseFile = "attachm2.answer";
        responseContentType = "multipart/related; charset=UTF-8; "
                + "boundary=jetty771207119h3h10dty";

        addUtf8BomToRequestFile = true;
        addUtf8BomToResponseFile = true;
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse) {
    }
}
