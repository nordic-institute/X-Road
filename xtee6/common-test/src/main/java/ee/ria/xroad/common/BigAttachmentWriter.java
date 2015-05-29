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
