package ee.ria.xroad.common;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.commons.lang3.tuple.Pair;

/**
 * Encapsulates necessary information about a simple request.
 */
public class RequestInputDataSimple extends RequestInputData {

    private String contentType;

    /**
     * Creates a simple request.
     * @param clientUrl the client URL
     * @param testRequest the test request
     * @param contentType content type of the request
     */
    public RequestInputDataSimple(String clientUrl, TestRequest testRequest,
            String contentType) {
        super(clientUrl, testRequest);
        this.contentType = contentType;
    }

    @Override
    public Pair<String, InputStream> getRequestInput() {
        try {
            return Pair.of(contentType, getRequestContentInputStream());
        } catch (Exception e) {
            throw new RuntimeException("Cannot create request input", e);
        }
    }

    private InputStream getRequestContentInputStream() throws Exception {
        return new ByteArrayInputStream(testRequest.getContent().getBytes());
    }
}
