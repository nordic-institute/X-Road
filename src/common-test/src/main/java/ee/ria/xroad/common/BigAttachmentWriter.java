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
import java.util.Random;

import lombok.extern.slf4j.Slf4j;

/**
 * Writes big attachment type test request content.
 */
@Slf4j
public class BigAttachmentWriter extends MultipartWriter {

    private static final int RANDOM_BLOCK_SIZE = 1024;

    private static final byte[] RANDOM_BLOCK = new byte[RANDOM_BLOCK_SIZE];

    private Integer attachmentSize;

    static {
        new Random().nextBytes(RANDOM_BLOCK);
    }

    /**
     * Constructs a writer for the given output stream and test request.
     * Will write random data until it covers the provided attachment size.
     * @param mpos output stream to which this writer should write the content
     * @param testRequest the test request this writer should write into the stream
     * @param attachmentSize the big attachment size
     * @throws IOException if I/O error occurred
     */
    public BigAttachmentWriter(PipedOutputStream mpos, TestRequest testRequest,
            int attachmentSize)
            throws IOException {
        super(mpos, testRequest);
        this.attachmentSize = attachmentSize;

        log.debug("Creating big attachment writer, attachment size: '{}'",
                attachmentSize);
    }

    @Override
    protected void writeAttachment() throws IOException {
        for (int i = 0; i <= attachmentSize; i += RANDOM_BLOCK.length) {
            mpos.write(RANDOM_BLOCK);
        }
    }
}
