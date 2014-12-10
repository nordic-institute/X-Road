package ee.cyber.sdsb.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Stack;

import org.apache.commons.io.IOUtils;

import static ee.cyber.sdsb.common.util.MimeUtils.randomBoundary;

public class MultipartEncoder implements AutoCloseable {

    private static final byte[] CRLF = {'\r', '\n'};
    private static final byte[] DASHDASH = {'-', '-'};

    protected final Stack<String> parts = new Stack<>();

    protected final String topBoundary;

    protected final OutputStream out;

    protected boolean inPart = false;

    public MultipartEncoder(OutputStream out) {
        this(out, randomBoundary());
    }

    public MultipartEncoder(OutputStream out, String topBoundary) {
        this.out = out;
        this.topBoundary = topBoundary;

        parts.add(topBoundary);
    }

    /**
     * Returns content type for the encoded message.
     */
    public String getContentType() {
        return MimeUtils.mpMixedContentType(topBoundary);
    }

    /**
     * Returns top-level boundary.
     */
    public String getBoundary() {
        return topBoundary;
    }

    public void startNested() throws IOException {
        startNested(randomBoundary());
    }

    public void startNested(String boundary) throws IOException {
        startPart(MimeUtils.mpMixedContentType(boundary));

        parts.add(boundary);
    }

    public void startNested(String boundary, String[] headers)
            throws IOException {
        startPart(MimeUtils.mpMixedContentType(boundary), headers);

        parts.add(boundary);
    }

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
     */
    public void startPart(String contentType) throws IOException {
        startPart(contentType, null);
    }

    /**
     * Starts new MIME part. Use write() method to write content of the part.
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
     */
    public void writeRawPart(InputStream content) throws IOException {
        writeCurrentBoundary();
        write(content);
    }

    public void write(byte[] content) throws IOException {
        out.write(content);
    }

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
