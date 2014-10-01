package ee.cyber.sdsb.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jetty.util.MultiPartOutputStream;


public class RequestInputDataMultipart extends RequestInputData {

    private byte[] soapBytes;
    private InputStream attachmentInputStream;
    private int attachmentSize;

    /**
     * Creates everything necessary for multipart request. If
     * {@link InputStream} for attachment is provided, it is used, if not, big
     * attachment with random content is created.
     *
     * @param attachmentInputStream
     *            - if null, big attachment with random content will be created.
     */
    public RequestInputDataMultipart(String clientUrl, TestRequest testRequest,
            int attachmentSize) {
        super(clientUrl, testRequest);
        this.attachmentSize = attachmentSize;
    }

    public RequestInputDataMultipart(byte[] soapBytes,
            InputStream attachmentInputStream) {
        super(null);
        this.soapBytes = soapBytes;
        this.attachmentInputStream = attachmentInputStream;
    }

    @Override
    public Pair<String, InputStream> getRequestInput() throws IOException {
        PipedOutputStream os = new PipedOutputStream();
        MultipartWriter mpWriter = attachmentInputStream == null
                ? new BigAttachmentWriter(os, testRequest, attachmentSize)
                : new CustomAttachmentWriter(os, soapBytes,
                        attachmentInputStream);

        PipedInputStream is = new PipedInputStream(os);
        MultiPartOutputStream mpos = mpWriter.getMultipartOutputStream();

        new Thread(mpWriter).start();

        return Pair.of("multipart/related; charset=UTF-8; "
                + "boundary=" + mpos.getBoundary(), (InputStream) is);
    }

}
