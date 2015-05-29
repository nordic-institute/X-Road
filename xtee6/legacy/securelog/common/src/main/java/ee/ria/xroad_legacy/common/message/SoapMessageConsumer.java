package ee.ria.xroad_legacy.common.message;

import java.io.InputStream;
import java.util.Map;

/**
 * Describes the SOAP message callback. The message consists of a XML message
 * and optional attachments.
 */
public interface SoapMessageConsumer {

    /**
     * Called when SOAP message is parsed.
     * @param message the SOAP message
     * @throws Exception if an error occurs
     */
    void soap(SoapMessage message) throws Exception;

    /**
     * Called when an attachment is received.
     * @param contentType the content type of the attachment
     * @param content the input stream holding the attachment data
     * @param additionalHeaders any additional headers for the attachment
     * @throws Exception if an error occurs
     */
    void attachment(String contentType, InputStream content,
            Map<String, String> additionalHeaders) throws Exception;
}
