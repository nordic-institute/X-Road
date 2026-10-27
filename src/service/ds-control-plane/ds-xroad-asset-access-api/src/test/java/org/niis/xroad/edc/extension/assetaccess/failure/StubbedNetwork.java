/*
 * The MIT License
 *
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

package org.niis.xroad.edc.extension.assetaccess.failure;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import okhttp3.Dns;
import okhttp3.OkHttpClient;

import javax.net.SocketFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Name resolution and connections for the shape tests' HTTP client, so the unreachable-provider cases depend on no free
 * port and no DNS server. A connection to {@link #REFUSING_HOST} is refused without touching the network, and
 * {@link #UNKNOWN_HOST} does not resolve. Every other host goes to the system resolver and a real socket.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class StubbedNetwork {
    static final String REFUSING_HOST = "refusing.provider.test";
    static final String UNKNOWN_HOST = "unknown.provider.test";

    private static final InetAddress REFUSING_ADDRESS = InetAddress.ofLiteral("192.0.2.1");

    static OkHttpClient.Builder httpClientBuilder() {
        return new OkHttpClient.Builder()
                .dns(StubbedNetwork::lookup)
                .socketFactory(new RefusingSocketFactory());
    }

    private static List<InetAddress> lookup(String hostname) throws UnknownHostException {
        return switch (hostname) {
            case REFUSING_HOST -> List.of(REFUSING_ADDRESS);
            case UNKNOWN_HOST -> throw new UnknownHostException(hostname);
            default -> Dns.SYSTEM.lookup(hostname);
        };
    }

    private static final class RefusingSocketFactory extends SocketFactory {
        private final SocketFactory delegate = SocketFactory.getDefault();

        @Override
        public Socket createSocket() {
            return new Socket() {
                @Override
                public void connect(SocketAddress endpoint, int timeout) throws IOException {
                    if (endpoint instanceof InetSocketAddress address && REFUSING_ADDRESS.equals(address.getAddress())) {
                        throw new ConnectException("Connection refused");
                    }
                    super.connect(endpoint, timeout);
                }
            };
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return delegate.createSocket(host, port);
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return delegate.createSocket(host, port, localHost, localPort);
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return delegate.createSocket(host, port);
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            return delegate.createSocket(address, port, localAddress, localPort);
        }
    }
}
