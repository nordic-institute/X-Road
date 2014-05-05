package ee.cyber.xroad.mediator.message;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.eclipse.jetty.http.MimeTypes;

import ee.cyber.sdsb.common.message.SoapMessage;
import ee.cyber.sdsb.common.util.MimeUtils;
import ee.cyber.sdsb.common.util.MultipartEncoder;

import static ee.cyber.sdsb.common.util.MimeUtils.toHeaders;

/**
 * Multipart message encoder that encodes SOAP messages as well as attachments
 * as different parts.
 */
public class MultipartMessageEncoder extends MultipartEncoder
        implements MessageEncoder {

    public MultipartMessageEncoder(OutputStream out) {
        super(out);
    }

    @Override
    public String getContentType() {
        return MimeUtils.mpRelatedContentType(topBoundary);
    }

    @Override
    public void soap(SoapMessage soapMessage) throws Exception {
        startPart(MimeTypes.TEXT_XML);
        write(soapMessage.getXml().getBytes(StandardCharsets.UTF_8));
    }

    public void attachment(String contentType, InputStream content,
            Map<String, String> additionalHeaders) throws Exception {
        startPart(contentType, toHeaders(additionalHeaders));
        write(content);
    }
}
