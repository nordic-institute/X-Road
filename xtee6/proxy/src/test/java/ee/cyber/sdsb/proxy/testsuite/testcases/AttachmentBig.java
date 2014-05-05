package ee.cyber.sdsb.proxy.testsuite.testcases;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Random;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jetty.util.MultiPartOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.util.MimeUtils;
import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;

/**
 * Cliet sends mesage with very big attachment. Server responds with
 * normal response.
 * Result: All OK.
 */
public class AttachmentBig extends MessageTestCase {
    private static final Logger LOG = LoggerFactory.getLogger(AttachmentBig.class);

    private static final int ATTACHMENT_SIZE_MBYTES = 650;

    private static final byte[] RANDOM_BLOCK = new byte[1024];

    static {
        new Random().nextBytes(RANDOM_BLOCK);
    }

    public AttachmentBig() throws Exception {
        responseFileName = "getstate.answer";
    }

    @Override
    protected Pair<String, InputStream> getRequestInput() throws Exception {
        PipedOutputStream os = new PipedOutputStream();
        PipedInputStream is = new PipedInputStream(os);
        MultiPartOutputStream mpos = new MultiPartOutputStream(os);

        new Thread(new MpWriter(mpos)).start();

        return Pair.of("multipart/related; charset=UTF-8; "
                    + "boundary=" + mpos.getBoundary(),
                (InputStream) is);
    }

    @Override
    protected int getClientTimeout() {
        // This test may take a long time.
        return 300000;
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse) {
        // All OK.
    }

    private class MpWriter implements Runnable {
        MultiPartOutputStream mpos;

        MpWriter(MultiPartOutputStream mpos) {
            this.mpos = mpos;
        }

        @Override
        public void run() {
            try {
                // Write SOAP message
                mpos.startPart(MimeUtils.TEXT_XML_UTF8);
                mpos.write(IOUtils.toByteArray(changeQueryId(
                        new FileInputStream(QUERIES_DIR + "/getstate.query"))));

                // Write attachment
                mpos.startPart("application/octet-stream",
                        new String[] {"Content-Transfer-Encoding: binary"});
                for (int i = 0; i < (ATTACHMENT_SIZE_MBYTES * 1000000);
                        i += RANDOM_BLOCK.length) {
                    mpos.write(RANDOM_BLOCK);
                }
                mpos.close();
            } catch (Exception ex) {
                LOG.error("Error when creating big attachment", ex);
            }
        }
    }
}
