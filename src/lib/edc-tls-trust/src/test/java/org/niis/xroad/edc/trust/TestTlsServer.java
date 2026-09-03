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
package org.niis.xroad.edc.trust;

import ee.ria.xroad.common.TestPortUtils;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import java.security.KeyStore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A minimal real TLS server presenting a given certificate chain, used to drive the socket-based
 * {@code X509ExtendedTrustManager} overloads a real client handshake exercises — the same overloads OkHttp's
 * connections use in production, and the ones {@code checkServerTrusted(chain, authType)} (no socket) never
 * sees.
 */
final class TestTlsServer implements AutoCloseable {

    private final SSLServerSocket serverSocket;
    private final ExecutorService acceptLoop = Executors.newSingleThreadExecutor(runnable -> {
        var thread = new Thread(runnable, "test-tls-server");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean stopped;

    private TestTlsServer(SSLServerSocket serverSocket) {
        this.serverSocket = serverSocket;
    }

    static TestTlsServer start(TestCa.TestLeaf serverCredentials) throws Exception {
        var keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("server", serverCredentials.privateKey(), new char[0], serverCredentials.chain());

        var keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, new char[0]);

        var sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), null, null);

        var port = TestPortUtils.findRandomPort();
        var serverSocket = (SSLServerSocket) sslContext.getServerSocketFactory().createServerSocket(port);
        var server = new TestTlsServer(serverSocket);
        server.acceptLoop.submit(server::acceptLoop);
        return server;
    }

    int port() {
        return serverSocket.getLocalPort();
    }

    private void acceptLoop() {
        while (!stopped) {
            try (var socket = serverSocket.accept()) {
                ((SSLSocket) socket).startHandshake();
            } catch (Exception ignored) {
                // Expected once the socket is closed on shutdown, and whenever a test client's handshake fails
                // (the point of this server: some tests deliberately drive a rejected handshake) — either way
                // the loop condition above decides whether another connection is accepted.
            }
        }
    }

    @Override
    public void close() throws Exception {
        stopped = true;
        serverSocket.close();
        acceptLoop.shutdownNow();
    }
}
