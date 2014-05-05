package ee.cyber.sdsb.common;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.commons.lang3.tuple.Pair;

public class RequestInputDataSimple implements RequestInputData {

    private String contentType;
    private TestQuery testQuery;

    public RequestInputDataSimple(TestQuery testQuery, String contentType) {
        this.testQuery = testQuery;
        this.contentType = contentType;
    }

    @Override
    public Pair<String, InputStream> getRequestInput() {
        try {
            return Pair.of(contentType, getQueryFileInputStream());
        } catch (Exception e) {
            throw new RuntimeException("Cannot create request input", e);
        }
    }

    @Override
    public String getQueryName() {
        return testQuery.getName();
    }

    private InputStream getQueryFileInputStream() throws Exception {
        return new ByteArrayInputStream(testQuery.getContent().getBytes());
    }
}
