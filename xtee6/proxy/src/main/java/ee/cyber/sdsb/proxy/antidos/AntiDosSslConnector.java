package ee.cyber.sdsb.proxy.antidos;

import java.io.IOException;
import java.net.Socket;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Semaphore;

import org.eclipse.jetty.io.nio.SelectChannelEndPoint;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.util.SystemMetrics;

/**
 * This class implements a SSL connector that prevents DoS attacks.
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
 *
 * TODO: Code duplication with AntiDosConnector
 */
public class AntiDosSslConnector extends SslSelectChannelConnector {

    private static final Logger LOG =
            LoggerFactory.getLogger(AntiDosSslConnector.class);

    private final AntiDosConnectionManager manager =
            new AntiDosConnectionManager() {
        @Override
        void closeConnection(SocketChannel sock) throws IOException {
            try {
                super.closeConnection(sock);
            } finally {
                onConnectionClosed();
            }
        }
    };

    private final Semaphore semaphore =
            new Semaphore(AntiDosConnector.MAX_PARALLEL_CONNECTIONS);

    public AntiDosSslConnector(SslContextFactory sslContextFactory) {
        super(sslContextFactory);
    }

    @Override
    protected void doStart() throws Exception {
        manager.init();

        super.doStart();

        getThreadPool().dispatch(new QueueManager());
    }

    @Override
    public void accept(int acceptorID) throws IOException {
        ServerSocketChannel server;
        synchronized (this) {
            server = _acceptChannel;
        }

        if (canAccept(server) && getSelectorManager().isStarted()) {
            SocketChannel channel = server.accept();
            channel.configureBlocking(false);

            Socket socket = channel.socket();
            configure(socket);

            LOG.debug("Accepted SSL connection: " + channel);
            manager.accept(channel);

            SystemMetrics.connectionAccepted();
        }
    }

    @Override
    protected void endPointClosed(SelectChannelEndPoint endpoint) {
        LOG.debug("Closed connection: " + endpoint);

        super.endPointClosed(endpoint);

        onConnectionClosed();
    }

    private void onConnectionClosed() {
        semaphore.release();

        SystemMetrics.connectionClosed();
    }

    private boolean canAccept(ServerSocketChannel server) {
        return manager.canAccept() && server != null && server.isOpen();
    }

    private class QueueManager implements Runnable {
        @Override
        public void run() {
            try {
                while (true) {
                    // Take the next connection to be processed
                    SocketChannel channel = manager.takeNextConnection();

                    // Wait until we can start processing
                    semaphore.acquire();

                    LOG.trace("Processing SSL connection: " + channel.socket());
                    getSelectorManager().register(channel);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
