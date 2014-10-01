package ee.cyber.sdsb.common;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.commons.lang3.tuple.Pair;

public class RequestInputDataSimple extends RequestInputData {

    private String contentType;

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
