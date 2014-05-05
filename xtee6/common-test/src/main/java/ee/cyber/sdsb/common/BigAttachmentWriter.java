package ee.cyber.sdsb.common;

import java.io.IOException;
import java.io.PipedOutputStream;
import java.util.Random;

public class BigAttachmentWriter extends MultipartWriter {
    private static final int ATTACHMENT_SIZE_BYTES = 100000000; // 681574400;
    private static final byte[] RANDOM_BLOCK = new byte[1024];

    static {
        new Random().nextBytes(RANDOM_BLOCK);
    }

    public BigAttachmentWriter(PipedOutputStream mpos, TestQuery testQuery)
            throws IOException {
        super(mpos, testQuery);
    }

    @Override
    protected void writeAttachment() throws IOException {
        for (int i = 0; i < ATTACHMENT_SIZE_BYTES; i += RANDOM_BLOCK.length) {
            mpos.write(RANDOM_BLOCK);
        }
    }
}
