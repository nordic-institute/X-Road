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
package ee.ria.xroad.common;

import java.io.IOException;
import java.io.PipedOutputStream;

import org.eclipse.jetty.util.MultiPartOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for multipart test request writers.
 */
public abstract class MultipartWriter implements Runnable {

    private static final Logger LOG = LoggerFactory
            .getLogger(MultipartWriter.class);

    private byte[] soapBytes;

    protected MultiPartOutputStream mpos;
    protected TestRequest testRequest;

    MultipartWriter(PipedOutputStream os, byte[] soapBytes) throws IOException {
        this.mpos = new MultiPartOutputStream(os);
        this.soapBytes = soapBytes;
    }

    MultipartWriter(PipedOutputStream os, TestRequest testRequest)
            throws IOException {
        this.mpos = new MultiPartOutputStream(os);
        this.testRequest = testRequest;
    }

    /**
     * @return the internal output stream this multipart writer is writing to
     */
    public MultiPartOutputStream getMultipartOutputStream() {
        return mpos;
    }

    @Override
    public void run() {
        try {
            // Write SOAP message
            mpos.startPart("text/xml");
            mpos.write(soapBytes != null ? soapBytes : testRequest.getContent()
                    .getBytes());

            startAttachmentPart();
            writeAttachment();
            mpos.close();
        } catch (Exception ex) {
            LOG.error("Error when creating big attachment", ex);
        }
    }

    protected void startAttachmentPart() throws IOException {
        mpos.startPart("application/octet-stream",
                new String[] {"Content-Transfer-Encoding: binary"});
    }

    protected abstract void writeAttachment() throws IOException;
}
