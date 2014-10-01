package ee.cyber.sdsb.common;

import java.io.IOException;
import java.io.InputStream;

import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Encapsulates necessary information about request
 */
public abstract class RequestInputData {
    protected TestRequest testRequest;

    @Getter
    private String clientUrl;

    public RequestInputData(String clientUrl) {
        this.clientUrl = clientUrl;
    }

    public RequestInputData(String clientUrl, TestRequest testRequest) {
        this(clientUrl);
        this.testRequest = testRequest;
    }

    /**
     * @return pair of content type and input stream
     */
    public abstract Pair<String, InputStream> getRequestInput()
            throws IOException;

    public String getRequestDescription() {
        if (testRequest == null) {
            return "";
        }

        return testRequest.getDescription();
    }

    public Integer getTimeoutSec() {
        if (testRequest == null) {
            return null;
        }

        return testRequest.getTimeoutSec();
    }
}
