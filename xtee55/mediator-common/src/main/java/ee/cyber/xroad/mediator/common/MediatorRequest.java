package ee.cyber.xroad.mediator.common;

import java.io.InputStream;

public interface MediatorRequest {

    String getContentType();

    InputStream getInputStream() throws Exception;

}
