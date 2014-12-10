package ee.cyber.xroad.common.message;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.util.MultiPartOutputStream;

import static ee.cyber.xroad.common.util.MimeUtils.contentTypeWithCharset;
import static ee.cyber.xroad.common.util.MimeUtils.mpRelatedContentType;
import static org.eclipse.jetty.http.MimeTypes.TEXT_XML;

/**
 * Encodes SOAP with attachments as MIME multipart.
 */
public class SoapMessageEncoder implements SoapMessageConsumer, Closeable {

    private MultiPartOutputStream multipart;

    public SoapMessageEncoder(OutputStream output) throws Exception {
        multipart = new MultiPartOutputStream(output);
    }

    public String getContentType() {
        return mpRelatedContentType(multipart.getBoundary());
    }

    @Override
    public void close() throws IOException {
        multipart.close();
    }

    @Override
    public void soap(SoapMessage soapMessage) throws Exception {
        String charset = soapMessage.getCharset();
        multipart.startPart(contentTypeWithCharset(TEXT_XML, charset));
        multipart.write(soapMessage.getXml().getBytes(charset));
    }

    @Override
    public void attachment(String contentType, InputStream content,
            Map<String, String> additionalHeaders) throws Exception {
        String[] headers = null;
        if (additionalHeaders != null && !additionalHeaders.isEmpty()) {
            headers = convertHeaders(additionalHeaders);
        }

        multipart.startPart(contentType, headers);
        IOUtils.copy(content, multipart);
    }

    private static String[] convertHeaders(Map<String, String> headers) {
        String[] ret = new String[headers.size()];
        int idx = 0;
        for (Map.Entry<String, String> entry: headers.entrySet()) {
            ret[idx++] = entry.getKey() + ": " + entry.getValue();
        }

        return ret;
    }
}
