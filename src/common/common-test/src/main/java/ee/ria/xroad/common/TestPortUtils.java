package ee.ria.xroad.common;

import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.net.ServerSocket;

@UtilityClass
public class TestPortUtils {

    public static Integer findRandomPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
