/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.proxy.testsuite.testcases;

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

import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

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

    /**
     * Constructs the test case.
     */
    public AttachmentBig() {
        responseFile = "getstate.answer";
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
