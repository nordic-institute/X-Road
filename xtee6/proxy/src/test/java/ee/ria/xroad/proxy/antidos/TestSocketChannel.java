package ee.ria.xroad.proxy.antidos;

import java.io.IOException;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@RequiredArgsConstructor
@EqualsAndHashCode
@ToString
class TestSocketChannel implements SocketChannelWrapper {

    private final String address;
    @Getter private boolean closed;

    @Override
    public String getHostAddress() {
        return address;
    }

    @Override
    public void close() throws IOException {
        closed = true;
    }
}
