package ee.cyber.sdsb.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jetty.util.MultiPartOutputStream;


public class RequestInputDataMultipart implements RequestInputData {

    private TestQuery testQuery;
    private byte[] soapBytes;
    private InputStream attachmentInputStream;

    /**
     * Creates everything necessary for multipart request. If
     * {@link InputStream} for attachment is provided, it is used, if not, big
     * attachment with random content is created.
     *
     * @param queryName
     * @param attachmentInputStream
     *            - if null, big attachment with random content will be created.
     */
    public RequestInputDataMultipart(TestQuery testQuery,
            InputStream attachmentInputStream) {
        this.testQuery = testQuery;
        this.attachmentInputStream = attachmentInputStream;
    }

    public RequestInputDataMultipart(byte[] soapBytes,
            InputStream attachmentInputStream) {
        this.soapBytes = soapBytes;
        this.attachmentInputStream = attachmentInputStream;
    }

    @Override
    public Pair<String, InputStream> getRequestInput() throws IOException {
        PipedOutputStream os = new PipedOutputStream();
        MultipartWriter mpWriter = attachmentInputStream == null
                ? new BigAttachmentWriter(os, testQuery)
                : new CustomAttachmentWriter(os, soapBytes,
                        attachmentInputStream);

        PipedInputStream is = new PipedInputStream(os);
        MultiPartOutputStream mpos = mpWriter.getMultipartOutputStream();

        new Thread(mpWriter).start();

        return Pair.of("multipart/related; charset=UTF-8; "
                + "boundary=" + mpos.getBoundary(), (InputStream) is);
    }

    @Override
    public String getQueryName() {
        return testQuery.getName();
    }

}
