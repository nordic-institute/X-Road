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

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Stack;

import static ee.ria.xroad.common.util.MimeUtils.randomBoundary;

/**
 * Writes multipart content into a output stream.
 */
@Slf4j
public class MultipartEncoder implements AutoCloseable {

    private static final byte[] CRLF = {'\r', '\n'};
    private static final byte[] DASHDASH = {'-', '-'};

    protected final Stack<String> parts = new Stack<>();

    protected final String topBoundary;

    protected final OutputStream out;

    protected boolean inPart = false;

    /**
     * Constructs a new multipart encoder with the given output stream.
     * @param out the output stream into which this encoder should write
     */
    public MultipartEncoder(OutputStream out) {
        this(out, randomBoundary());
    }

    /**
     * Constructs a new multipart encoder with the given output stream
     * and using the provided top-level boundary.
     * @param out the output stream into which this encoder should write
     * @param topBoundary the top-level boundary that should be used
     */
    public MultipartEncoder(OutputStream out, String topBoundary) {
        this.out = out;
        this.topBoundary = topBoundary;

        parts.add(topBoundary);
    }

    /**
     * @return content type for the encoded message.
     */
    public String getContentType() {
        return MimeUtils.mpMixedContentType(topBoundary);
    }

    /**
     * @return top-level boundary.
     */
    public String getBoundary() {
        return topBoundary;
    }

    /**
     * Begins a nested section with a random boundary.
     * @throws IOException in case an I/O error occurred
     */
    public void startNested() throws IOException {
        startNested(randomBoundary());
    }

    /**
     * Begins a nested section with the specified boundary.
     * @param boundary the boundary that should be used for this section
     * @throws IOException in case an I/O error occurred
     */
    public void startNested(String boundary) throws IOException {
        startPart(MimeUtils.mpMixedContentType(boundary));

        parts.add(boundary);
    }

    /**
     * Begins a nested section with the specified boundary and headers.
     * @param boundary the boundary that should be used for this section
     * @param headers headers that should be appended to the start of this section
     * @throws IOException in case an I/O error occurred
     */
    public void startNested(String boundary, String[] headers)
            throws IOException {
        startPart(MimeUtils.mpMixedContentType(boundary), headers);

        parts.add(boundary);
    }

    /**
     * Ends the current nested section.
     * @throws IOException in case an I/O error occurred
     */
    public void endNested() throws IOException {
        String currentBoundary = parts.pop();
        if (currentBoundary != null) {
            out.write(CRLF);
            out.write(DASHDASH);
            writeString(currentBoundary);
            out.write(DASHDASH);
            out.write(CRLF);
        }
    }

    /**
     * Starts new MIME part. Use write() method to write content of the part.
     * @param contentType content type of this MIME part
     * @throws IOException in case an I/O error occurred
     */
    public void startPart(String contentType) throws IOException {
        startPart(contentType, null);
    }

    /**
     * Starts new MIME part. Use write() method to write content of the part.
     * @param contentType content type of this MIME part
     * @param headers headers that should be appended to the start of this MIME part
     * @throws IOException in case an I/O error occurred
     */
    public void startPart(String contentType, String[] headers)
            throws IOException {
        writeCurrentBoundary();

        if (contentType != null) {
            writeString(MimeUtils.HEADER_CONTENT_TYPE + ": " + contentType);
            out.write(CRLF);
        }

        for (int i = 0; headers != null && i < headers.length; i++) {
            writeString(headers[i]);
            out.write(CRLF);
        }
        out.write(CRLF);
    }

    /**
     * Assumes that content contains already encoded MIME part
     * (header + body). It then writes the whole part in one operation.
     * @param content input stream containing raw MIME part content
     * @throws IOException in case an I/O error occurred
     */
    public void writeRawPart(InputStream content) throws IOException {
        writeCurrentBoundary();
        write(content);
    }

    /**
     * Writes the given bytes into the encoder's output stream.
     * @param content byte content to write
     * @throws IOException in case an I/O error occurred
     */
    public void write(byte[] content) throws IOException {
        out.write(content);
    }

    /**
     * Writes the given bytes into the encoder's output stream.
     * @param content byte content to write
     * @param offset offset to the buffer
     * @param len length to write
     * @throws IOException
     */
    public void write(byte[] content, int offset, int len) throws IOException {
        out.write(content, offset, len);
    }

    /**
     * Writes the content of the given input stream into the encoder's output stream.
     * @param content input stream containing the content to write
     * @throws IOException in case an I/O error occurred
     */
    public void write(InputStream content) throws IOException {
        IOUtils.copy(content, out);
    }

    @Override
    public void close() throws IOException {
        while (!parts.isEmpty()) {
            endNested();
        }

        try {
            out.flush();
        } catch (IOException ignored) {
            //ignore
            log.error("Error flushing multipart encoder stream", ignored);
        }
        out.close();
    }

    private void writeCurrentBoundary() throws IOException {
        if (inPart) {
            // Write CRLF to ensure that the boundary starts on new line.
            out.write(CRLF);
        }

        inPart = true;

        String boundary = parts.peek();
        out.write(DASHDASH);
        writeString(boundary);
        out.write(CRLF);
    }

    private void writeString(String string) throws IOException {
        out.write(string.getBytes(StandardCharsets.ISO_8859_1));
    }
}
