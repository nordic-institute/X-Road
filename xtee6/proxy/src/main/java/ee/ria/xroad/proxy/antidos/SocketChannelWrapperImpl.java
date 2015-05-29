package ee.ria.xroad.proxy.antidos;

import java.io.IOException;
import java.nio.channels.SocketChannel;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter(AccessLevel.PACKAGE)
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class SocketChannelWrapperImpl implements SocketChannelWrapper {

    private final SocketChannel channel;

    @Override
    public String getHostAddress() {
        return channel.socket().getInetAddress().getHostAddress();
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

}
