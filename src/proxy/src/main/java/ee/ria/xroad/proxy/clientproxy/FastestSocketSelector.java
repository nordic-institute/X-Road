/**
 * The MIT License
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

import lombok.AccessLevel;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.StandardSocketOptions;
import java.net.URI;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

import static org.apache.commons.io.IOUtils.closeQuietly;

/**
 * Given a list of addresses, selects the first one to respond.
 * More specifically, we initiate a connection to all specified addresses and
 * wait for any connection events using Selector. We return the first address
 * from the selector or null, if no connections can be made.
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class FastestSocketSelector {

    @Data
    static final class SocketInfo {
        private final URI uri;
        private final Socket socket;
    }

    private final URI[] addresses;
    private final int connectTimeout;

    SocketInfo select() throws IOException {
        log.trace("select()");
        Selector selector = Selector.open();
        try {
            initConnections(selector);
            SelectionKey key = selectFirstConnectedSocketChannel(selector);
            if (key == null) {
                return null;
            }
            final SocketChannel channel = (SocketChannel) key.channel();
            key.cancel();
            channel.configureBlocking(true);
            return new SocketInfo((URI) key.attachment(), channel.socket());
        } finally {
            try {
                closeSelector(selector);
            } catch (Exception e) {
                log.error("Error while closing selector", e);
            }
        }
    }

    private SelectionKey selectFirstConnectedSocketChannel(Selector selector) throws IOException {
        log.trace("selectFirstConnectedSocketChannel()");

        while (!selector.keys().isEmpty()) {
            if (selector.select(connectTimeout) == 0) { // Block until something happens
                return null;
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

        return null;
    }

    private boolean isConnected(SelectionKey key) {
        if (key.isValid() && key.isConnectable()) {
            SocketChannel channel = (SocketChannel) key.channel();
            try {
                return channel.finishConnect();
            } catch (Exception e) {
                key.cancel();
                closeQuietly(channel);
                log.trace("Error connecting socket channel: {}", e);
            }
        }
        return false;
    }

    private void initConnections(Selector selector) {
        log.trace("initConnections()");

        for (URI target : addresses) {
            final InetSocketAddress address = new InetSocketAddress(target.getHost(), target.getPort());
            if (address.isUnresolved()) {
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
