package ee.cyber.sdsb.common;

import java.io.IOException;
import java.io.PipedOutputStream;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BigAttachmentWriter extends MultipartWriter {
    private static final Logger LOG = LoggerFactory
            .getLogger(BigAttachmentWriter.class);

    private static final byte[] RANDOM_BLOCK = new byte[1024];

    private Integer attachmentSize;

    static {
        new Random().nextBytes(RANDOM_BLOCK);
    }

    public BigAttachmentWriter(PipedOutputStream mpos, TestRequest testRequest,
            int attachmentSize)
            throws IOException {
        super(mpos, testRequest);
        this.attachmentSize = attachmentSize;

        LOG.debug("Creating big attachment writer, attachment size: '{}'",
                attachmentSize);
    }

    @Override
    protected void writeAttachment() throws IOException {
        for (int i = 0; i <= attachmentSize; i += RANDOM_BLOCK.length) {
            mpos.write(RANDOM_BLOCK);
        }
    }
}
