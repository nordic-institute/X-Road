package ee.ria.xroad.proxy.antidos;

import java.io.IOException;
import java.net.Socket;
import java.nio.channels.ServerSocketChannel;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.io.nio.SelectChannelEndPoint;
import org.eclipse.jetty.server.nio.SelectChannelConnector;

/**
 * This class implements a connector that prevents DoS attacks.
 *
 * Since it is important to accept all incoming requests thus giving a chance
 * for non-attackers requests to be processed, we must handle the potential
 * situation of running out of free file handles (or running low on some other
 * system resources).
 *
 * When the algorithm detects low resource situation, it closes a number of
 * oldest connections not yet processed. This means that an attacker who is
 * making lots of connections only gets a small amount of connections
 * "approved", and is a trade-off since non-attackers connections might
 * also get closed.
 */
@Slf4j
public class AntiDosConnector extends SelectChannelConnector {

    private final AntiDosConnectorDelegate delegate;

    /**
     * Construct a new AntiDos connector.
     */
    public AntiDosConnector() {
        super();

        this.delegate = new AntiDosConnectorDelegate(this) {
            @Override
            void configure(Socket sock) throws IOException {
                AntiDosConnector.this.configure(sock);
            }
        };
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        delegate.doStart();
    }

    @Override
    public void accept(int acceptorID) throws IOException {
        ServerSocketChannel server;
        synchronized (this) {
            server = _acceptChannel;
        }
        try {
            delegate.accept(server);
        } catch (Throwable err) {
            log.error("Error accepting connection:", err);
            server.close();
        }
    }

    @Override
    protected void endPointClosed(SelectChannelEndPoint endpoint) {
        super.endPointClosed(endpoint);

        delegate.endPointClosed(endpoint);
    }
}
