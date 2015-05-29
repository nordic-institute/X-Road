package ee.ria.xroad.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedOutputStream;

import org.apache.commons.io.IOUtils;

/**
 * Writes custom attachment test request content.
 */
public class CustomAttachmentWriter extends MultipartWriter {

    private InputStream attachmentInputStream;

    /**
     * Constructs a writer for the given output stream and test request bytes.
     * Will attach contents of the custom attachment input stream to the request.
     * @param os output stream to which this writer should write the content
     * @param soapBytes bytes of the test request
     * @param attachmentInputStream input stream containing custom attachment data
     * @throws IOException if I/O error occurred
     */
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
