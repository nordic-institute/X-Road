package ee.cyber.xroad.mediator.client;

import java.io.OutputStream;
import java.util.Map;

/**
 * Declares methods of a mediator response.
 */
public interface MediatorResponse {

    /**
     * Sets content type of the data of the response and any additional HTTP headers.
     * @param contentType the content type
     * @param additionalHeaders additional HTTP headers
     */
    void setContentType(String contentType,
            Map<String, String> additionalHeaders);

    /**
     * @return the output stream where response data should be written
     * @throws Exception in case of any errors
     */
    OutputStream getOutputStream() throws Exception;

}
