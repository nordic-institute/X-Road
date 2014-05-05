package ee.cyber.xroad.mediator.common;

import java.io.OutputStream;
import java.util.Map;

public interface MediatorResponse {

    void setContentType(String contentType,
            Map<String, String> additionalHeaders);

    OutputStream getOutputStream() throws Exception;

}
