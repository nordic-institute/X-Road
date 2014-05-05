package ee.cyber.sdsb.proxy.clientproxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.StandardSocketOptions;
import java.net.URI;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

import lombok.Data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Given a list of addresses, selects the first one to respond.
 * More specifically, we initiate a connection to all specified addresses and
 * wait for any connection events using Selector. We return the first address
 * from the selector or null, if no connections can be made.
 */
class FastestSocketSelector {

    @Data
    static final class SocketInfo {
        private final URI uri;
        private final Socket socket;
    }

    private static final Logger LOG =
            LoggerFactory.getLogger(FastestSocketSelector.class);

    private final URI[] addresses;
    private final int connectTimeout;

    FastestSocketSelector(URI[] addresses, int connectTimeout) {
        this.addresses = addresses;
        this.connectTimeout = connectTimeout;
    }

    SocketInfo select() throws IOException {
        LOG.trace("select()");

        Selector selector = Selector.open();

        SocketInfo selectedSocket = initConnections(selector);
        if (selectedSocket != null) {
            return selectedSocket;
        }

        URI selectedAddress = null;
        SocketChannel channel = null;
        try {
            SelectionKey key = selectFirstConnectedSocketChannel(selector);
            if (key == null) {
                return null;
            }

            channel = (SocketChannel) key.channel();
            selectedAddress = (URI) key.attachment();
        } finally {
            try {
                selector.close();
            } catch (Exception e) {
                LOG.error("Error while closing selector", e);
            }
        }

        if (channel != null) {
            channel.configureBlocking(true);
            return new SocketInfo(selectedAddress, channel.socket());
        }

        return null;
    }

    private SelectionKey selectFirstConnectedSocketChannel(Selector selector)
            throws IOException {
        LOG.trace("selectFirstConnectedSocketChannel()");

        while (!selector.keys().isEmpty()) {
            if (selector.select(connectTimeout) == 0) { // Block until something happens
                return null;
            }

            Iterator<SelectionKey> it = selector.selectedKeys().iterator();
            while (it.hasNext()) {
                SelectionKey key = it.next();
                if (key.isValid() && key.isConnectable()) {
                    SocketChannel channel = (SocketChannel) key.channel();
                    try {
                        if (channel.finishConnect()) {
                            return key;
                        }
                    } catch (Exception e) {
                        key.cancel();
                        silentClose(channel);

                        LOG.trace("Error connecting socket channel: {}",
                                e.getMessage());
                    }
                }

                it.remove();
            }
        }

        return null;
    }

    private SocketInfo initConnections(Selector selector) throws IOException {
        LOG.trace("initConnections()");

        for (URI target : addresses) {
            SocketChannel channel = SocketChannel.open();
            channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
            channel.configureBlocking(false);
            try {
                channel.register(selector, SelectionKey.OP_CONNECT, target);
                if (channel.connect(new InetSocketAddress(target.getHost(),
                        target.getPort()))) { // connected immediately
                    channel.configureBlocking(true);
                    return new SocketInfo(target, channel.socket());
                }
            } catch (Exception e) {
                silentClose(channel);

                LOG.trace("Error connecting socket channel ({}): {}", target,
                        e);
            }
        }

        return null;
    }

    private static void silentClose(SocketChannel channel) {
        try {
            channel.close();
        } catch (Exception ignore) {
        }

    }
}
