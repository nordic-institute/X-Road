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
import java.io.InputStream;
import java.io.PipedOutputStream;

import org.apache.commons.io.IOUtils;

/**
 * Writes custom attachment test request content.
 */
public class CustomAttachmentWriter extends MultipartWriter {

    private InputStream attachmentInputStream;

    /**
     * Constructs a writer for the given output stream and test request bytes.
     * Will attach contents of the custom attachment input stream to the request.
     * @param os output stream to which this writer should write the content
     * @param soapBytes bytes of the test request
     * @param attachmentInputStream input stream containing custom attachment data
     * @throws IOException if I/O error occurred
     */
    public CustomAttachmentWriter(PipedOutputStream os, byte[] soapBytes,
            InputStream attachmentInputStream)
            throws IOException {
        super(os, soapBytes);
        this.attachmentInputStream = attachmentInputStream;
    }

    @Override
    protected void writeAttachment() throws IOException {
        IOUtils.copy(attachmentInputStream, mpos);
    }
}
