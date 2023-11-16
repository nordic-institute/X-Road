/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import ee.ria.xroad.common.util.SystemMetrics;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.io.SelectorManager;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.Scheduler;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

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
public class AntiDosConnector extends ServerConnector {

    private final AntiDosConfiguration configuration = new AntiDosConfiguration();

    private final Semaphore semaphore = new Semaphore(configuration.getMaxParallelConnections());

    private final AntiDosConnectionManager<SocketChannelWrapperImpl> manager =
            new AntiDosConnectionManager<SocketChannelWrapperImpl>(configuration) {
                @Override
                void closeConnection(SocketChannelWrapperImpl sock) throws IOException {
                    try {
                        super.closeConnection(sock);
                    } finally {
                        onConnectionClosed();
                    }
                }
            };

    /**
     * Construct a new AntiDos connector.
     *
     * @param server the server
     * @param acceptorCount acceptor count
     */
    public AntiDosConnector(Server server, int acceptorCount) {
        super(server, acceptorCount, -1);
    }

    /**
     * Constructs a new SSL-enabled AntiDos connector.
     *
     * @param server the server
     * @param acceptorCount acceptor count
     * @param sslContextFactory SSL context factory to use for configuration
     */
    public AntiDosConnector(Server server, int acceptorCount, SslContextFactory.Server sslContextFactory) {
        super(server, acceptorCount, -1, sslContextFactory);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        manager.init();

        getExecutor().execute(new QueueManager());
    }

    @Override
    public void accept(int acceptorID) throws IOException {
        if (manager.canAccept()) {
            super.accept(acceptorID);
        }
    }

    @Override
    protected void onEndPointClosed(EndPoint endpoint) {
        super.onEndPointClosed(endpoint);

        log.trace("Closed connection: " + endpoint);

        onConnectionClosed();
    }

    protected void onConnectionClosed() {
        semaphore.release();

        log.trace("Released a permit, current total: {}", semaphore.availablePermits());

        SystemMetrics.connectionClosed();
    }

    @Override
    protected SelectorManager newSelectorManager(Executor executor, Scheduler scheduler, int selectors) {
        return new ServerConnectorManager(executor, scheduler, selectors) {
            @Override
            public void accept(SelectableChannel channel) {
                log.trace("Accepted connection: " + channel);

                manager.accept(new SocketChannelWrapperImpl((SocketChannel) channel));

                SystemMetrics.connectionAccepted();
            }
        };
    }

    private class QueueManager implements Runnable {
        @Override
        public void run() {
            try {
                while (true) {
                    // Take the next connection to be processed
                    SocketChannel channel = manager.takeNextConnection().getChannel();

                    if (channel.isOpen() && !channel.isRegistered()) {
                        log.trace("Looking to acquire permit, current total: {}", semaphore.availablePermits());

                        // Wait until we can start processing
                        semaphore.acquire();

                        log.trace("Processing connection: " + channel.socket());

                        getSelectorManager().accept(channel, null);
                    }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
