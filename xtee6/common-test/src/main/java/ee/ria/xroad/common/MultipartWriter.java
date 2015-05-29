package ee.ria.xroad.common;

import java.io.IOException;
import java.io.PipedOutputStream;

import org.eclipse.jetty.util.MultiPartOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for multipart test request writers.
 */
public abstract class MultipartWriter implements Runnable {

    private static final Logger LOG = LoggerFactory
            .getLogger(MultipartWriter.class);

    private byte[] soapBytes;

    protected MultiPartOutputStream mpos;
    protected TestRequest testRequest;

    MultipartWriter(PipedOutputStream os, byte[] soapBytes) throws IOException {
        this.mpos = new MultiPartOutputStream(os);
        this.soapBytes = soapBytes;
    }

    MultipartWriter(PipedOutputStream os, TestRequest testRequest)
            throws IOException {
        this.mpos = new MultiPartOutputStream(os);
        this.testRequest = testRequest;
    }

    /**
     * @return the internal output stream this multipart writer is writing to
     */
    public MultiPartOutputStream getMultipartOutputStream() {
        return mpos;
    }

    @Override
    public void run() {
        try {
            // Write SOAP message
            mpos.startPart("text/xml");
            mpos.write(soapBytes != null ? soapBytes : testRequest.getContent()
                    .getBytes());

            startAttachmentPart();
            writeAttachment();
            mpos.close();
        } catch (Exception ex) {
            LOG.error("Error when creating big attachment", ex);
        }
    }

    protected void startAttachmentPart() throws IOException {
        mpos.startPart("application/octet-stream",
                new String[] {"Content-Transfer-Encoding: binary"});
    }

    protected abstract void writeAttachment() throws IOException;
}
