package ee.ria.xroad.common.message;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import ee.ria.xroad.common.util.MimeUtils;

/**
 * Output stream that produces MIME multipart content.
 */
public class MultipartOutputStream extends FilterOutputStream {

    private static final byte[] NEWLINE = {'\r', '\n'};
    private static final byte[] DASHES = {'-', '-'};

    private final String boundary;
    private final byte[] boundaryBytes;

    private boolean inPart = false;

    /**
     * Constructs new instance
     * @param out the target output stream
     * @throws IOException if an error occurs
     */
    public MultipartOutputStream(OutputStream out) {
        this(out, randomBoundary());
    }

    /**
     * Constructs new instance
     * @param out the target output stream
     * @param boundary the boundary value to use
     * @throws IOException if an error occurs
     */
    public MultipartOutputStream(OutputStream out, String boundary) {
        super(out);

        this.boundary = boundary;
        this.boundaryBytes = bytes(boundary);

        this.inPart = false;
    }

    @Override
    public void close() throws IOException {
        if (inPart) {
            out.write(NEWLINE);
        }

        out.write(DASHES);
        out.write(boundaryBytes);
        out.write(DASHES);
        out.write(NEWLINE);

        inPart = false;

        super.close();
    }

    /**
     * @return the boundary
     */
    public String getBoundary() {
        return boundary;
    }

    /**
     * Starts next part.
     * @param contentType the content type of the part
     * @param additionalHeaders additional headers
     * @throws IOException if an error occurs
     */
    public void startPart(String contentType, String... additionalHeaders)
            throws IOException {
        if (inPart) {
            out.write(NEWLINE);
        }

        inPart = true;

        out.write(DASHES);
        out.write(boundaryBytes);
        out.write(NEWLINE);

        out.write(bytes((MimeUtils.HEADER_CONTENT_TYPE + ":" + contentType)));
        out.write(NEWLINE);

        for (String header : additionalHeaders) {
            out.write(bytes(header));
            out.write(NEWLINE);
        }

        out.write(NEWLINE);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
    }

    private static String randomBoundary() {
        return "xroad" + MimeUtils.randomBoundary();
    }

    private static byte[] bytes(String string) {
        return string.getBytes(StandardCharsets.ISO_8859_1);
    }
}
