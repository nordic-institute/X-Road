package ee.ria.xroad.common;

import java.io.IOException;
import java.io.InputStream;

import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Encapsulates necessary information about request.
 */
public abstract class RequestInputData {
    protected TestRequest testRequest;

    @Getter
    private String clientUrl;

    /**
     * Constructs a new request input data object for the given client URL.
     * @param clientUrl the client URL
     */
    public RequestInputData(String clientUrl) {
        this.clientUrl = clientUrl;
    }

    /**
     * Constructs a new request input data object holding the provided test
     * request for the given client URL.
     * @param clientUrl the client URL
     * @param testRequest the test request
     */
    public RequestInputData(String clientUrl, TestRequest testRequest) {
        this(clientUrl);
        this.testRequest = testRequest;
    }

    /**
     * @return pair of content type and input stream
     * @throws IOException if an I/O error occurred
     */
    public abstract Pair<String, InputStream> getRequestInput()
            throws IOException;

    /**
     * @return description of the test request or an empty string if it is null
     */
    public String getRequestDescription() {
        if (testRequest == null) {
            return "";
        }

        return testRequest.getDescription();
    }

    /**
     * @return timeout of the test request or null if it is null
     */
    public Integer getTimeoutSec() {
        if (testRequest == null) {
            return null;
        }

        return testRequest.getTimeoutSec();
    }

    /**
     * @return size of the request input data
     * @throws IOException if an I/O error occurred
     */
    public long getSize() throws IOException {
        return getRequestInput().getRight().available();
    }
}
