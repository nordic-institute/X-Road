package ee.ria.xroad_legacy.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Stack;

import org.apache.commons.io.IOUtils;

import static ee.ria.xroad_legacy.common.util.MimeUtils.randomBoundary;

public class MultipartEncoder {

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

    public void startNested() throws IOException {
        startNested(randomBoundary());
    }

    public void startNested(String boundary) throws IOException {
        startPart(MimeUtils.mpMixedContentType(boundary));

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

    public void startPart(String contentType) throws IOException {
        startPart(contentType, null);
    }

    public void startPart(String contentType, String[] headers)
            throws IOException {
        String boundary = parts.peek();

        if (inPart) {
            out.write(CRLF);
        }

        inPart = true;

        out.write(DASHDASH);
        writeString(boundary);
        out.write(CRLF);

        writeString(MimeUtils.HEADER_CONTENT_TYPE + ": " + contentType);
        out.write(CRLF);

        for (int i = 0; headers != null && i < headers.length; i++) {
            writeString(headers[i]);
            out.write(CRLF);
        }

        out.write(CRLF);
    }

    public void write(byte[] content) throws IOException {
        out.write(content);
    }

    public void write(InputStream content) throws IOException {
        IOUtils.copy(content, out);
    }

    public void write(Reader content) throws IOException {
        IOUtils.copy(content, out);
    }

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

    private void writeString(String string) throws IOException {
        out.write(string.getBytes(StandardCharsets.ISO_8859_1));
    }
}
