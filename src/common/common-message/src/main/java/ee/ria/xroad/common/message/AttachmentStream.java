package ee.ria.xroad.common.message;

import java.io.InputStream;

public interface AttachmentStream {
    InputStream getStream();

    long getSize();

    static AttachmentStream fromInputStream(InputStream stream, long size) {
        return new AttachmentStream() {
            @Override
            public InputStream getStream() {
                return stream;
            }

            @Override
            public long getSize() {
                return size;
            }
        };
    }
}
