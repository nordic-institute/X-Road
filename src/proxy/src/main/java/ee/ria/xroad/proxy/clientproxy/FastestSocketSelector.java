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
package ee.ria.xroad.proxy.clientproxy;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.net.SocketFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.StandardSocketOptions;
import java.net.URI;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static ee.ria.xroad.proxy.clientproxy.FastestConnectionSelectingSSLSocketFactory.closeQuietly;

/**
 * Given a list of addresses, selects the first one to respond.
 * More specifically, we initiate a connection to all specified addresses and
 * wait for any connection events using Selector. We return the first address
 * from the selector or null, if no connections can be made.
 *
 * Note! During selection, the selector will remove addresses from the provided list if the address is
 * unresolvable or there is an error during connecting to the address.
 */
@Slf4j
final class FastestSocketSelector {

    @Data
    static final class SocketInfo {
        private final URI uri;
        private final Socket socket;
    }

    private List<URI> addresses = new ArrayList<>();

    void add(URI address) {
        addresses.add(address);
    }

    void addAll(URI... address) {
        for (URI u : address) {
            addresses.add(u);
        }
    }

    boolean remove(URI address) {
        return addresses.remove(address);
    }

    boolean isEmpty() {
        return addresses.isEmpty();
    }

    SocketInfo select(int timeout) throws IOException {
        switch (addresses.size()) {
            case 0:
                throw new IOException("No addresses to select from");
            case 1:
                return connect(timeout);
            default:
                log.info("Selecting the fastest connection from following addresses: {}", addresses);
                return doSelect(timeout);
        }
    }

    @SuppressWarnings("squid:S2095")
    private SocketInfo connect(int timeout) throws IOException {
        final URI uri = addresses.get(0);
        Socket socket = null;
        try {
            socket = SocketFactory.getDefault().createSocket();
            final InetSocketAddress address = new InetSocketAddress(uri.getHost(), uri.getPort());
            socket.connect(address, timeout);
            return new SocketInfo(uri, socket);
        } catch (Exception e) {
            addresses.remove(uri);
            closeQuietly(socket);
            throw e;
        }
    }

    private SocketInfo doSelect(int timeout) throws IOException {
        log.trace("select()");
        Selector selector = Selector.open();
        try {
            initConnections(selector);
            SelectionKey key = selectFirstConnectedSocketChannel(selector, timeout);
            final SocketChannel channel = (SocketChannel)key.channel();
            key.cancel();
            channel.configureBlocking(true);
            return new SocketInfo((URI)key.attachment(), channel.socket());
        } finally {
            try {
                closeSelector(selector);
            } catch (Exception e) {
                log.error("Error while closing selector", e);
            }
        }
    }

    private SelectionKey selectFirstConnectedSocketChannel(Selector selector, long connectTimeout) throws
            IOException {
        log.trace("selectFirstConnectedSocketChannel()");

        while (!selector.keys().isEmpty()) {
            if (selector.select(connectTimeout) == 0) { // Block until something happens
                break;
            }
            Iterator<SelectionKey> it = selector.selectedKeys().iterator();
            while (it.hasNext()) {
                SelectionKey key = it.next();
                if (isConnected(key)) {
                    return key;
                }
                it.remove();
            }
        }
        throw new IOException("Unable to connect to any of the provided addresses.");
    }

    private boolean isConnected(SelectionKey key) {
        if (key.isValid() && key.isConnectable()) {
            SocketChannel channel = (SocketChannel)key.channel();
            try {
                return channel.finishConnect();
            } catch (Exception e) {
                //connection failed, do not consider this address any more
                addresses.remove(key.attachment());
                key.cancel();
                closeQuietly(channel);
                log.trace("Error connecting socket channel: {}", e.getMessage());
            }
        }
        return false;
    }

    private void initConnections(Selector selector) {
        log.trace("initConnections()");

        for (Iterator<URI> iterator = addresses.iterator(); iterator.hasNext();) {
            final URI target = iterator.next();
            final InetSocketAddress address = new InetSocketAddress(target.getHost(), target.getPort());
            if (address.isUnresolved()) {
                iterator.remove();
                continue;
            }
            SocketChannel channel = null;
            SelectionKey key = null;
            try {
                channel = SocketChannel.open();
                channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
                channel.configureBlocking(false);
                key = channel.register(selector, SelectionKey.OP_CONNECT, target);
                if (channel.connect(address)) {
                    // connected immediately
                    break;
                }
            } catch (Exception e) {
                if (key != null) {
                    key.cancel();
                    iterator.remove();
                }
                closeQuietly(channel);
                log.trace("Error connecting to '{}': {}", target, e);
            }
        }
    }

    private static void closeSelector(Selector selector) throws IOException {
        for (SelectionKey key : selector.keys()) {
            if (key.isValid()) {
                closeQuietly(key.channel());
            }
        }
        selector.close();
    }
}
