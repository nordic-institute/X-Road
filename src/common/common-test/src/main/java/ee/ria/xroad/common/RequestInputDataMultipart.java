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
package ee.ria.xroad.common;

import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jetty.util.MultiPartOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;


/**
 * Encapsulates necessary information about a multipart request.
 */
public class RequestInputDataMultipart extends RequestInputData {

    private byte[] soapBytes;
    private InputStream attachmentInputStream;
    private int attachmentSize;

    /**
     * Creates a multipart request with an attachment of random data of specified size.
     * @param clientUrl the client URL
     * @param testRequest the test request object
     * @param attachmentSize size of the random big attachment data
     */
    public RequestInputDataMultipart(String clientUrl, TestRequest testRequest,
            int attachmentSize) {
        super(clientUrl, testRequest);
        this.attachmentSize = attachmentSize;
    }

    /**
     * Creates a multipart request. Uses the provided input stream for the attachment data.
     * @param soapBytes byte content of the request
     * @param attachmentInputStream if null, big attachment with random content will be created
     */
    public RequestInputDataMultipart(byte[] soapBytes,
            InputStream attachmentInputStream) {
        super(null);
        this.soapBytes = soapBytes;
        this.attachmentInputStream = attachmentInputStream;
    }

    @Override
    public Pair<String, InputStream> getRequestInput() throws IOException {
        PipedOutputStream os = new PipedOutputStream();
        MultipartWriter mpWriter = attachmentInputStream == null
                ? new BigAttachmentWriter(os, testRequest, attachmentSize)
                : new CustomAttachmentWriter(os, soapBytes,
                        attachmentInputStream);

        PipedInputStream is = new PipedInputStream(os);
        MultiPartOutputStream mpos = mpWriter.getMultipartOutputStream();

        new Thread(mpWriter).start();

        return Pair.of("multipart/related; charset=UTF-8; "
                + "boundary=" + mpos.getBoundary(), (InputStream) is);
    }

    @Override
    public long getSize() throws IOException {
        return (attachmentInputStream == null)
                ? testRequest.getContent().getBytes().length + attachmentSize
                : soapBytes.length + attachmentInputStream.available();
    }
}
