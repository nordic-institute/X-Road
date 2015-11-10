package ee.cyber.xroad.mediator.message;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import ee.ria.xroad.common.message.SoapMessage;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.common.util.MultipartEncoder;

import static ee.ria.xroad.common.util.MimeUtils.toHeaders;

/**
 * Multipart message encoder that encodes SOAP messages as well as attachments
 * as different parts.
 */
public class MultipartMessageEncoder extends MultipartEncoder
        implements MessageEncoder {

    /**
     * Constructs a multipart message encoder with the given output stream.
     * @param out the output stream
     * @param topBoundary boundary
     */
    public MultipartMessageEncoder(OutputStream out, String topBoundary) {
        super(out, topBoundary);
    }

    @Override
    public String getContentType() {
        return MimeUtils.mpRelatedContentType(topBoundary);
    }

    @Override
    public void soap(SoapMessage soapMessage,
            Map<String, String> additionalHeaders) throws Exception {
        startPart(soapMessage.getContentType(),
                MimeUtils.toHeaders(additionalHeaders));
        write(soapMessage.getXml().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Encodes an attachment with the specified content type.
     * @param contentType the content type of the attachment
     * @param content the input stream containing attachment data
     * @param additionalHeaders additional HTTP headers
     * @throws Exception in case of any errors
     */
    public void attachment(String contentType, InputStream content,
            Map<String, String> additionalHeaders) throws Exception {
        startPart(contentType, toHeaders(additionalHeaders));
        write(content);
    }
}
