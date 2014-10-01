package ee.cyber.sdsb.proxy.antidos;

import java.io.IOException;

public interface SocketChannelWrapper {

    String getHostAddress();

    void close() throws IOException;

}
