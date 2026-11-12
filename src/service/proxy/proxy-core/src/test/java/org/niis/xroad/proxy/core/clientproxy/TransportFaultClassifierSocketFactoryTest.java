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

import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.util.HttpSender;

import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.ProxyConfigKeys;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.niis.xroad.proxy.core.clientproxy.AuthTrustVerifier.ID_PROVIDERNAME;
import static org.niis.xroad.proxy.core.clientproxy.FastestConnectionSelectingSSLSocketFactory.ID_TARGETS;

/**
 * Feeds real network failures that pass through {@link FastestConnectionSelectingSSLSocketFactory}
 * to {@link TransportFaultClassifier}.
 */
class TransportFaultClassifierSocketFactoryTest {

    private static final int CONNECT_TIMEOUT_MILLIS = 300;
    private static final int READ_TIMEOUT_MILLIS = 300;
    private static final int BACKLOG_FILL_TIMEOUT_MILLIS = 200;
    private static final int MAX_BACKLOG_FILLERS = 50;
    private static final byte[] TLS_HANDSHAKE_FAILURE_ALERT = {0x15, 0x03, 0x03, 0x00, 0x02, 0x02, 0x28};
    private static final char[] KEY_PASSWORD = new char[0];

    private final Set<Socket> openSockets = ConcurrentHashMap.newKeySet();
    private CloseableHttpClient httpClient;
    private ServerSocket server;

    @BeforeEach
    void setUp() throws GeneralSecurityException {
        var proxyProperties = new ProxyProperties(XRoadConfigBuilder.create().register(ProxyConfigKeys.instance()).build());
        var clientTls = SSLContext.getInstance("TLS");
        clientTls.init(null, new TrustManager[]{new TrustAnyServer()}, null);
        var socketFactory = new FastestConnectionSelectingSSLSocketFactory(mock(AuthTrustVerifier.class),
                clientTls.getSocketFactory(), proxyProperties, new UnusableAddressTracker(proxyProperties));
        var connectionManager = new PoolingHttpClientConnectionManager(
                RegistryBuilder.<ConnectionSocketFactory>create().register("https", socketFactory).build());
        httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setRetryHandler(new DefaultHttpRequestRetryHandler(0, false))
                .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        httpClient.close();
        if (server != null) {
            server.close();
        }
        openSockets.forEach(socket -> {
            try {
                socket.close();
            } catch (IOException _) {
                // best effort cleanup
            }
        });
    }

    @Test
    void closedPortMeansNothingWasSent() throws IOException {
        server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        var closedPortAddress = address(server);
        server.close();

        assertThat(TransportFaultClassifier.nothingWasSent(sendFailure(closedPortAddress))).isTrue();
    }

    @Test
    void silentlyDroppedConnectMeansNothingWasSent() throws IOException {
        server = listenerWithFullBacklog();

        assertThat(TransportFaultClassifier.nothingWasSent(sendFailure(address(server)))).isTrue();
    }

    @Test
    void handshakeAlertFromServerMeansNothingWasSent() throws IOException {
        server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        serveEachConnection(socket -> {
            readSome(socket);
            write(socket, TLS_HANDSHAKE_FAILURE_ALERT);
        });

        assertThat(TransportFaultClassifier.nothingWasSent(sendFailure(address(server)))).isTrue();
    }

    @Test
    void serverThatStallsAfterHandshakeMeansTheRequestWasSent() throws GeneralSecurityException, IOException {
        server = tlsServerSocket();
        serveEachConnection(TransportFaultClassifierSocketFactoryTest::readSome);

        assertThat(TransportFaultClassifier.nothingWasSent(sendFailure(address(server)))).isFalse();
    }

    private Throwable sendFailure(URI target) {
        return catchThrowable(() -> {
            try (var sender = new HttpSender(httpClient, false)) {
                sender.setConnectionTimeout(CONNECT_TIMEOUT_MILLIS);
                sender.setSocketTimeout(READ_TIMEOUT_MILLIS);
                sender.setAttribute(ID_TARGETS, new URI[]{target});
                sender.setAttribute(ID_PROVIDERNAME, ServiceId.Conf.create("DEV", "COM", "222", "TESTSERVICE", "petstore"));
                sender.doPost(target, "request", "text/plain");
            }
        });
    }

    private static URI address(ServerSocket listener) {
        return URI.create("https://127.0.0.1:%d/".formatted(listener.getLocalPort()));
    }

    private ServerSocket listenerWithFullBacklog() throws IOException {
        var listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        for (int i = 0; i < MAX_BACKLOG_FILLERS; i++) {
            var filler = new Socket();
            openSockets.add(filler);
            try {
                filler.connect(listener.getLocalSocketAddress(), BACKLOG_FILL_TIMEOUT_MILLIS);
            } catch (SocketTimeoutException _) {
                return listener;
            }
        }
        listener.close();
        return fail("The listener's backlog never filled up");
    }

    private static ServerSocket tlsServerSocket() throws GeneralSecurityException, IOException {
        var serverCertificate = TestCertUtil.getProducer();
        var keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("server", serverCertificate.key, KEY_PASSWORD, serverCertificate.certChain);
        var keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keyStore, KEY_PASSWORD);
        var serverTls = SSLContext.getInstance("TLS");
        serverTls.init(keyManagers.getKeyManagers(), null, null);

        return serverTls.getServerSocketFactory().createServerSocket(0, 1, InetAddress.getLoopbackAddress());
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
            if (socket instanceof SSLSocket tlsSocket) {
                tlsSocket.startHandshake();
            }
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

    private static final class TrustAnyServer implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            // test server certificates are trusted without validation
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            // test server certificates are trusted without validation
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
