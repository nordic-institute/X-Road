package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

/**
 * The simplest case -- normal message and normal response. Both messages
 * have the UTF-8 BOM bytes.
 * Result: client receives message.
 */
public class Utf8BomNormalSubsystem extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public Utf8BomNormalSubsystem() {
        requestFileName = "getstate-subsystem.query";
        responseFile = "getstate-subsystem.answer";

        addUtf8BomToRequestFile = true;
        addUtf8BomToResponseFile = true;
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse)
            throws Exception {
        // Normal response, nothing more to check here.
    }
}
