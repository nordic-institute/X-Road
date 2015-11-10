package ee.ria.xroad.common.message;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;

import static ee.ria.xroad.common.util.MimeUtils.mpRelatedContentType;

/**
 * Encodes SOAP with attachments as MIME multipart.
 */
public class SoapMessageEncoder implements SoapMessageConsumer, Closeable {

    private MultipartOutputStream multipart;

    /**
     * Creates a SOAP message encoder that writes messages to the given
     * output stream.
     * @param output output stream to write SOAP messages
     */
    public SoapMessageEncoder(OutputStream output) {
        this(output, null);
    }

    /**
     * Creates a SOAP message encoder that writes messages to the given
     * output stream.
     * @param output output stream to write SOAP messages
     * @param boundary the MIME boundary value to use
     */
    public SoapMessageEncoder(OutputStream output, String boundary) {
        if (boundary == null) {
            multipart = new MultipartOutputStream(output);
        } else {
            multipart = new MultipartOutputStream(output, boundary);
        }
    }

    /**
     * @return the content-type string for multipart/related content with the
     * current boundary.
     */
    public String getContentType() {
        return mpRelatedContentType(multipart.getBoundary());
    }

    @Override
    public void close() throws IOException {
        multipart.close();
    }

    @Override
    public void soap(SoapMessage soapMessage,
            Map<String, String> additionalHeaders) throws Exception {
        multipart.startPart(soapMessage.getContentType(),
                convertHeaders(additionalHeaders));
        multipart.write(soapMessage.getBytes());
    }

    @Override
    public void attachment(String contentType, InputStream content,
            Map<String, String> additionalHeaders) throws Exception {
        String[] headers = {};
        if (additionalHeaders != null && !additionalHeaders.isEmpty()) {
            headers = convertHeaders(additionalHeaders);
        }

        multipart.startPart(contentType, headers);
        IOUtils.copy(content, multipart);
    }

    private static String[] convertHeaders(Map<String, String> headers) {
        return headers.entrySet().stream()
            .map(e -> e.getKey() + ": " + e.getValue())
            .collect(Collectors.toList())
            .toArray(new String[] {});
    }
}
