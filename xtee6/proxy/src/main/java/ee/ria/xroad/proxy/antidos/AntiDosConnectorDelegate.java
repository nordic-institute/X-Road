/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.proxy.antidos;

import java.io.IOException;
import java.net.Socket;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Semaphore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.io.nio.SelectChannelEndPoint;
import org.eclipse.jetty.server.nio.SelectChannelConnector;

import ee.ria.xroad.common.util.SystemMetrics;

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
