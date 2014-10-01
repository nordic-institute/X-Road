package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.common.message.SoapUtils;
import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

/**
 * Async message but send directly with X-Ignore-Async HTTP header.
 */
public class AsyncIgnoreMessage extends MessageTestCase {
    public AsyncIgnoreMessage() {
        requestFileName = "async.query";
        responseFileName = "getstate.answer";

        addRequestHeader(SoapUtils.X_IGNORE_ASYNC, "true");
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse)
            throws Exception {
        // Normal response, nothing more to check here.
    }
}
