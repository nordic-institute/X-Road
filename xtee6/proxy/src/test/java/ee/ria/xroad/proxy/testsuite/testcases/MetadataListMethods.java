package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

/**
 * The simplest case -- normal message and normal response.
 * Result: client receives message.
 */
public class MetadataListMethods extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public MetadataListMethods() {
        //requestFileName = "listMethods.query"; // TODO currently disabled
        requestFileName = "getstate.query";
        responseFile = "getstate.answer";
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse)
            throws Exception {
        // Normal response, nothing more to check here.
    }
}
