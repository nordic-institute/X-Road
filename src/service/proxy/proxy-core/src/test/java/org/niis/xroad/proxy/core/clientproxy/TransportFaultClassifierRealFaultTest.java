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
package org.niis.xroad.proxy.core.clientproxy;

import ee.ria.xroad.common.util.HttpSender;

import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.niis.xroad.common.core.exception.ErrorCode.HTTP_ERROR;

class TransportFaultClassifierRealFaultTest {

    private static final int READ_TIMEOUT_MILLIS = 300;
    private static final byte[] TLS_HANDSHAKE_FAILURE_ALERT = {0x15, 0x03, 0x03, 0x00, 0x02, 0x02, 0x28};

    private final Set<Socket> openSockets = ConcurrentHashMap.newKeySet();
    private CloseableHttpClient httpClient;
    private ServerSocket server;

    @BeforeEach
    void setUp() throws IOException {
        httpClient = HttpClients.createDefault();
        server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
    }

    @AfterEach
    void tearDown() throws IOException {
        httpClient.close();
        server.close();
        openSockets.forEach(socket -> {
            try {
                socket.close();
            } catch (IOException _) {
                // best effort cleanup
            }
        });
    }

    @Test
    void closedPortIsATransportFault() throws IOException {
        int closedPort = server.getLocalPort();
        server.close();

        assertThat(sendFailure("http", closedPort))
                .satisfies(TransportFaultClassifierRealFaultTest::assertTransportFault);
    }

    @Test
    void rejectedTlsHandshakeIsATransportFault() {
        serveEachConnection(socket -> {
            readSome(socket);
            write(socket, TLS_HANDSHAKE_FAILURE_ALERT);
        });

        assertThat(sendFailure("https", server.getLocalPort()))
                .satisfies(TransportFaultClassifierRealFaultTest::assertTransportFault);
    }

    @Test
    void serverThatNeverAnswersIsATransportFault() {
        serveEachConnection(socket -> {
        });

        assertThat(sendFailure("http", server.getLocalPort()))
                .satisfies(TransportFaultClassifierRealFaultTest::assertTransportFault);
    }

    @Test
    void unresolvableHostIsATransportFault() throws IOException {
        var connectionManager = new PoolingHttpClientConnectionManager(
                RegistryBuilder.<ConnectionSocketFactory>create()
                        .register("http", PlainConnectionSocketFactory.getSocketFactory())
                        .build(),
                null, host -> {
                    throw new UnknownHostException(host);
                });
        try (var unresolvingClient = HttpClients.custom().setConnectionManager(connectionManager).build()) {
            var failure = catchThrowable(() -> {
                try (var sender = new HttpSender(unresolvingClient, false)) {
                    sender.doPost(URI.create("http://unresolvable.provider.test/"), "request", "text/plain");
                }
            });
            assertThat(failure).satisfies(TransportFaultClassifierRealFaultTest::assertTransportFault);
        }
    }

    @Test
    void serverAnsweringWithInternalErrorRaisesNothing() {
        serveEachConnection(socket -> {
            readSome(socket);
            write(socket, ("HTTP/1.1 500 Internal Server Error\r\nContent-Type: text/xml\r\nContent-Length: 1\r\n"
                    + "Connection: close\r\n\r\nx").getBytes(StandardCharsets.US_ASCII));
        });

        assertThatCode(() -> {
            try (var sender = newSender()) {
                sender.doPost(uri("http", server.getLocalPort()), "request", "text/plain");
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void serverAnsweringWithErrorStatusIsNotATransportFault() {
        serveEachConnection(socket -> {
            readSome(socket);
            write(socket, "HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                    .getBytes(StandardCharsets.US_ASCII));
        });

        assertThat(sendFailure("http", server.getLocalPort())).satisfies(failure -> {
            assertThat(failure).isInstanceOfSatisfying(XrdRuntimeException.class,
                    e -> assertThat(e.isCausedBy(HTTP_ERROR)).isTrue());
            assertThat(TransportFaultClassifier.isTransportFault(failure)).isFalse();
        });
    }

    private static void assertTransportFault(Throwable failure) {
        assertThat(TransportFaultClassifier.isTransportFault(failure)).isTrue();
    }

    private Throwable sendFailure(String scheme, int port) {
        return catchThrowable(() -> {
            try (var sender = newSender()) {
                sender.doPost(uri(scheme, port), "request", "text/plain");
            }
        });
    }

    private HttpSender newSender() {
        var sender = new HttpSender(httpClient, false);
        sender.setSocketTimeout(READ_TIMEOUT_MILLIS);
        return sender;
    }

    private static URI uri(String scheme, int port) {
        return URI.create("%s://127.0.0.1:%d/".formatted(scheme, port));
    }

    private void serveEachConnection(Consumer<Socket> handler) {
        Thread.ofVirtual().start(() -> {
            while (!server.isClosed()) {
                try {
                    var socket = server.accept();
                    openSockets.add(socket);
                    Thread.ofVirtual().start(() -> handler.accept(socket));
                } catch (IOException _) {
                    return;
                }
            }
        });
    }

    private static void readSome(Socket socket) {
        try {
            socket.getInputStream().read(new byte[8192]);
        } catch (IOException _) {
            // the client may already have gone
        }
    }

    private static void write(Socket socket, byte[] bytes) {
        try {
            socket.getOutputStream().write(bytes);
            socket.getOutputStream().flush();
        } catch (IOException _) {
            // the client may already have gone
        }
    }
}
