/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jetty.util.MultiPartOutputStream;

import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

/**
 * Cliet sends mesage with very big attachment. Server responds with
 * normal response.
 * Result: All OK.
 */
@Slf4j
public class AttachmentBig extends MessageTestCase {

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
    protected Pair<String, InputStream> getRequestInput(boolean addUtf8Bom)
            throws Exception {
        PipedOutputStream os = new PipedOutputStream();
        PipedInputStream is = new PipedInputStream(os);
        MultiPartOutputStream mpos = new MultiPartOutputStream(os);

        if (addUtf8Bom) {
            mpos.write(ByteOrderMark.UTF_8.getBytes());
        }

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
            Path path = Paths.get(QUERIES_DIR + "/getstate.query");
            try (InputStream in = changeQueryId(Files.newInputStream(path))) {
                // Write SOAP message
                mpos.startPart(MimeTypes.TEXT_XML_UTF8);
                mpos.write(IOUtils.toByteArray(in));

                // Write attachment
                mpos.startPart("application/octet-stream",
                        new String[] {"Content-Transfer-Encoding: binary"});
                for (int i = 0; i < (ATTACHMENT_SIZE_MBYTES * 1000000);
                        i += RANDOM_BLOCK.length) {
                    mpos.write(RANDOM_BLOCK);
                }
                mpos.close();
            } catch (Exception ex) {
                log.error("Error when creating big attachment", ex);
            }
        }
    }
}
