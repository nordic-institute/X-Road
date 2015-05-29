package ee.cyber.xroad.mediator.common;

import java.io.InputStream;

/**
 * Declares methods of a mediator request.
 */
public interface MediatorRequest {

    /**
     * @return the content type of the data of the request
     */
    String getContentType();

    /**
     * @return the input stream with request data
     * @throws Exception in case of any errors
     */
    InputStream getInputStream() throws Exception;

    /**
     * @return the parameters string of the request URL
     */
    String getParameters();
}
