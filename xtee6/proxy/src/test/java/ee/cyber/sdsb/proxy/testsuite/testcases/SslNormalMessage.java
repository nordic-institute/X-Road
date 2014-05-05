package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.SslMessageTestCase;

/**
 * The simplest case -- normal message and normal response.
 * Result: client receives message.
 */
public class SslNormalMessage extends SslMessageTestCase {
    public SslNormalMessage() {
        requestFileName = "getstate.query";
        responseFileName = "getstate.answer";
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse)
            throws Exception {
        // Normal response, nothing more to check here.
    }
}
