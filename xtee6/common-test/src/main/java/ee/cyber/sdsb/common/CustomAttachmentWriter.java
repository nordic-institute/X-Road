package ee.cyber.sdsb.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedOutputStream;

import org.apache.commons.io.IOUtils;

public class CustomAttachmentWriter extends MultipartWriter {

    private InputStream attachmentInputStream;

    public CustomAttachmentWriter(PipedOutputStream os, byte[] soapBytes,
            InputStream attachmentInputStream)
            throws IOException {
        super(os, soapBytes);
        this.attachmentInputStream = attachmentInputStream;
    }

    @Override
    protected void writeAttachment() throws IOException {
        IOUtils.copy(attachmentInputStream, mpos);
    }
}
