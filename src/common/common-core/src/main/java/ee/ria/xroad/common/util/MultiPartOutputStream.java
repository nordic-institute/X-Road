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
package ee.ria.xroad.common.util;


import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static ee.ria.xroad.common.util.MimeUtils.randomBoundary;

/**
 * Handle a multipart MIME response. Taken from Jetty 12.
 */
public class MultiPartOutputStream extends FilterOutputStream {

    private static final byte[] CRLF = {'\r', '\n'};
    private static final byte[] DASHDASH = {'-', '-'};

    public static final String MULTIPART_MIXED = "multipart/mixed";
    public static final String MULTIPART_X_MIXED_REPLACE = "multipart/x-mixed-replace";

    private final String boundary;
    private final byte[] boundaryBytes;

    private boolean inPart = false;

    public MultiPartOutputStream(OutputStream out)
            throws IOException {
        super(out);

        boundary = "jetty" + randomBoundary();
        boundaryBytes = boundary.getBytes(StandardCharsets.ISO_8859_1);
    }

    public MultiPartOutputStream(OutputStream out, String boundary) throws IOException {
        super(out);

        this.boundary = boundary;
        boundaryBytes = boundary.getBytes(StandardCharsets.ISO_8859_1);
    }

    /**
     * End the current part.
     *
     * @throws IOException IOException
     */
    @Override
    public void close()
            throws IOException {
        try {
            if (inPart)
                out.write(CRLF);
            out.write(DASHDASH);
            out.write(boundaryBytes);
            out.write(DASHDASH);
            out.write(CRLF);
            inPart = false;
        } finally {
            super.close();
        }
    }

    public String getBoundary() {
        return boundary;
    }

    public OutputStream getOut() {
        return out;
    }

    /**
     * Start creation of the next Content.
     *
     * @param contentType the content type of the part
     * @throws IOException if unable to write the part
     */
    public void startPart(String contentType)
            throws IOException {
        if (inPart) {
            out.write(CRLF);
        }
        inPart = true;
        out.write(DASHDASH);
        out.write(boundaryBytes);
        out.write(CRLF);
        if (contentType != null) {
            out.write(("Content-Type: " + contentType).getBytes(StandardCharsets.ISO_8859_1));
            out.write(CRLF);
        }
        out.write(CRLF);
    }

    /**
     * Start creation of the next Content.
     *
     * @param contentType the content type of the part
     * @param headers     the part headers
     * @throws IOException if unable to write the part
     */
    public void startPart(String contentType, String[] headers)
            throws IOException {
        if (inPart)
            out.write(CRLF);
        inPart = true;
        out.write(DASHDASH);
        out.write(boundaryBytes);
        out.write(CRLF);
        if (contentType != null) {
            out.write(("Content-Type: " + contentType).getBytes(StandardCharsets.ISO_8859_1));
            out.write(CRLF);
        }
        for (int i = 0; headers != null && i < headers.length; i++) {
            out.write(headers[i].getBytes(StandardCharsets.ISO_8859_1));
            out.write(CRLF);
        }
        out.write(CRLF);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
    }
}
