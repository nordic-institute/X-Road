package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

/**
 * Async message which must end up in Async-DB
 */
public class AsyncMessage extends MessageTestCase {
    public AsyncMessage() {
        requestFileName = "async.query";
        responseFileName = "getstate.answer";
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse)
            throws Exception {
        // Normal response, nothing more to check here.
    }
}
