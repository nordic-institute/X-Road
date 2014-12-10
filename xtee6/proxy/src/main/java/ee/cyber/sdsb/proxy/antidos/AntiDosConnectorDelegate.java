package ee.cyber.sdsb.proxy.antidos;

import java.io.IOException;
import java.net.Socket;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Semaphore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.eclipse.jetty.io.nio.SelectChannelEndPoint;
import org.eclipse.jetty.server.nio.SelectChannelConnector;

import ee.cyber.sdsb.common.util.SystemMetrics;

@Slf4j
@RequiredArgsConstructor
class AntiDosConnectorDelegate {

    private static final AntiDosConfiguration CONF =
            new AntiDosConfiguration();

    private final Semaphore semaphore =
            new Semaphore(CONF.getMaxParallelConnections());

    private final AntiDosConnectionManager<SocketChannelWrapperImpl> manager =
            new AntiDosConnectionManager<SocketChannelWrapperImpl>(CONF) {
        @Override
        void closeConnection(SocketChannelWrapperImpl sock) throws IOException {
            try {
                super.closeConnection(sock);
            } finally {
                onConnectionClosed();
            }
        }
    };

    private final SelectChannelConnector connector;

    void doStart() throws Exception {
        manager.init();

        connector.getThreadPool().dispatch(new QueueManager());
    }

    void accept(ServerSocketChannel server) throws IOException {
        if (canAccept(server) && connector.getSelectorManager().isStarted()) {
            SocketChannel channel = server.accept();
            channel.configureBlocking(false);

            Socket socket = channel.socket();
            configure(socket);

            log.trace("Accepted connection: " + channel);
            manager.accept(new SocketChannelWrapperImpl(channel));

            SystemMetrics.connectionAccepted();
        }
    }

    void endPointClosed(SelectChannelEndPoint endpoint) {
        log.trace("Closed connection: " + endpoint);

        onConnectionClosed();
    }

    void onConnectionClosed() {
        semaphore.release();

        log.trace("Released a permit, current total: {}",
                semaphore.availablePermits());
        SystemMetrics.connectionClosed();
    }

    void configure(Socket sock) throws IOException {
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
                    SocketChannel channel =
                            manager.takeNextConnection().getChannel();
                    
                    log.trace("Looking to acquire permit, current total: {}",
                            semaphore.availablePermits());
                    // Wait until we can start processing
                    semaphore.acquire();

                    log.trace("Processing connection: " + channel.socket());
                    connector.getSelectorManager().register(channel);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
