package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

/**
 * Async message which must end up in Async-DB
 */
public class AsyncMessage extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public AsyncMessage() {
        requestFileName = "async.query";
        responseFile = "getstate.answer";
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse)
            throws Exception {
        // Normal response, nothing more to check here.
    }
}
