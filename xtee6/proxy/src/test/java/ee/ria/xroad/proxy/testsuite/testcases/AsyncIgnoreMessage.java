package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.common.message.SoapUtils;
import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

/**
 * Async message but send directly with X-Ignore-Async HTTP header.
 */
public class AsyncIgnoreMessage extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public AsyncIgnoreMessage() {
        requestFileName = "async.query";
        responseFile = "getstate.answer";

        addRequestHeader(SoapUtils.X_IGNORE_ASYNC, "true");
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse)
            throws Exception {
        // Normal response, nothing more to check here.
    }
}
