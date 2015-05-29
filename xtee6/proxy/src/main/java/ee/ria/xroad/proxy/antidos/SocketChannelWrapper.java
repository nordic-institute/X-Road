package ee.ria.xroad.proxy.antidos;

import java.io.IOException;

interface SocketChannelWrapper {

    String getHostAddress();

    void close() throws IOException;

}
