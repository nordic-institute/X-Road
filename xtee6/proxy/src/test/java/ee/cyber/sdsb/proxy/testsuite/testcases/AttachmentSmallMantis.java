package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.common.util.MimeTypes;
import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

public class AttachmentSmallMantis extends MessageTestCase {

    /* This test trys to mimic xtee5 Mantis issue #0003796
     * both small and big attachment have made into one mime message.
     *
     * NB! Nothing significant found. And at the moment I see it as a
     * replica of testcase AttachmentBig.
     * */

    public AttachmentSmallMantis() throws Exception {
        requestFileName = "attachment-small-mantis.query";
        requestContentType = "multipart/related; start-info=" +
                MimeTypes.TEXT_XML + "; type='application/xop+xml'; " +
                "boundary=:gkMa5KAvuHP7ahe9IQHONLgnncvv:";

        responseFileName = "attachment-small-mantis.answer";
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse)
            throws Exception {
        // Normal response, nothing more to check here.
    }
}
