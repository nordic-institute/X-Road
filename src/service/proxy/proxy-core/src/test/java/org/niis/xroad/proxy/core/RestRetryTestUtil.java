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
package org.niis.xroad.proxy.core;

import ee.ria.xroad.common.DiagnosticsStatus;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.keyconf.dto.AuthKey;
import org.niis.xroad.messagelog.LogMessage;
import org.niis.xroad.messagelog.RestLogMessage;
import org.niis.xroad.messagelog.SoapLogMessage;
import org.niis.xroad.messagelog.TimestampRecord;
import org.niis.xroad.proxy.core.addon.messagelog.AbstractLogManager;
import org.niis.xroad.proxy.core.service.ServiceAddressResolver;
import org.niis.xroad.proxy.core.test.DummySslServerProxy;
import org.niis.xroad.proxy.core.util.ProxyRequestContext;
import org.niis.xroad.serverconf.ServerConfProvider;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static ee.ria.xroad.common.TestCertUtil.getCaCert;
import static org.niis.xroad.common.properties.DefaultTlsProperties.DEFAULT_XROAD_SSL_CIPHER_SUITES;
import static org.niis.xroad.common.properties.DefaultTlsProperties.PROXY_TLS_PROTOCOLS;

/**
 * Shared helpers for the client proxy TLS handshake retry integration tests.
 */
public final class RestRetryTestUtil {

    private RestRetryTestUtil() {
    }

    /**
     * Builds a server proxy address for the given local port.
     */
    public static URI uri(int port) {
        return URI.create("https://127.0.0.1:%d/".formatted(port));
    }

    /**
     * Starts a TLS endpoint that presents the given auth key (so the client-side trust
     * verification passes at connect time) but rejects the client certificate, producing the
     * delayed TLS 1.3 handshake failure during request execution. The rejection is ordered
     * through the given gate: it is sent only once the client proxy has finished establishing
     * the connection and has stopped writing request bytes, so the failure deterministically
     * surfaces mid-request instead of at connect time.
     */
    public static RejectingSslServerProxy startRejectingProxy(int port, AuthKey authKey, HandshakeOrderGate gate)
            throws Exception {
        var certChain = authKey.certChain().getAllCertsWithoutTrustedRoot().toArray(new X509Certificate[0]);
        var keyManager = new DummySslServerProxy.DummyAuthKeyManager() {
            @Override
            public X509Certificate[] getCertificateChain(String alias) {
                return certChain;
            }

            @Override
            public PrivateKey getPrivateKey(String alias) {
                return authKey.key();
            }
        };
        return new RejectingSslServerProxy("127.0.0.1", port, keyManager, gate);
    }

    /**
     * Orders the rejecting proxy's TLS alert relative to the connecting client proxy's progress.
     * The client side releases a permit each time the client proxy finishes establishing a
     * connection (after {@code AuthTrustVerifier.verify(...)} returns); the rejecting proxy's
     * trust manager takes a permit before rejecting the client certificate. This guarantees the
     * rejection never interrupts connection establishment, without betting on a wall-clock delay.
     */
    @Slf4j
    public static final class HandshakeOrderGate {

        private static final long AWAIT_TIMEOUT_SECONDS = 30;

        private final Semaphore clientPastConnect = new Semaphore(0);

        /** Client side: signals that the client proxy has finished establishing a connection. */
        public void clientPastConnect() {
            clientPastConnect.release();
        }

        /** Rejecting proxy side: waits until the client proxy has finished establishing a connection. */
        void awaitClientPastConnect() throws InterruptedException {
            if (!clientPastConnect.tryAcquire(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("The client proxy never signalled connection establishment; rejecting anyway");
            }
        }

        /** Drops permits left over from connections that were not rejected (e.g. to the real server proxy). */
        public void reset() {
            clientPastConnect.drainPermits();
        }
    }

    /**
     * Minimal TLS server that performs the proxy-proxy handshake but rejects the client
     * certificate, reproducing the TLS 1.3 deferred client authentication failure. Unlike a
     * Jetty-based dummy, it keeps a handle on the plain TCP socket, so the rejection can be
     * held until the connecting client has gone quiet — i.e. has written the request as far
     * as it ever will — making the phase in which the failure surfaces deterministic instead
     * of timing-dependent.
     */
    @Slf4j
    public static final class RejectingSslServerProxy {

        private final KeyManager keyManager;
        private final HandshakeOrderGate gate;
        private final ServerSocket serverSocket;
        private final Set<Socket> openSockets = ConcurrentHashMap.newKeySet();

        RejectingSslServerProxy(String host, int port, KeyManager keyManager, HandshakeOrderGate gate)
                throws IOException {
            this.keyManager = keyManager;
            this.gate = gate;
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(host, port));
            Thread.ofVirtual().name("rejecting-proxy-" + port).start(this::acceptConnections);
        }

        /** Stops accepting connections and closes the open ones. */
        public void destroy() {
            closeQuietly(serverSocket);
            openSockets.forEach(RejectingSslServerProxy::closeQuietly);
        }

        private void acceptConnections() {
            while (!serverSocket.isClosed()) {
                try {
                    Socket plainSocket = serverSocket.accept();
                    openSockets.add(plainSocket);
                    Thread.ofVirtual().start(() -> rejectConnection(plainSocket));
                } catch (IOException e) {
                    return; // the server socket was closed
                }
            }
        }

        /**
         * Drives the TLS handshake into the client certificate rejection; the handshake
         * failing is the expected outcome.
         */
        private void rejectConnection(Socket plainSocket) {
            try (plainSocket) {
                layerSslSocket(plainSocket).startHandshake();
                log.warn("The TLS handshake unexpectedly succeeded");
            } catch (GeneralSecurityException | IOException e) {
                log.trace("Rejected a connection", e);
            } finally {
                openSockets.remove(plainSocket);
            }
        }

        private SSLSocket layerSslSocket(Socket plainSocket) throws GeneralSecurityException, IOException {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(new KeyManager[]{keyManager},
                    new TrustManager[]{new RejectingClientTrustManager(gate, plainSocket)}, new SecureRandom());
            var sslSocket = (SSLSocket) ctx.getSocketFactory().createSocket(
                    plainSocket, plainSocket.getInetAddress().getHostAddress(), plainSocket.getPort(), false);
            sslSocket.setUseClientMode(false);
            sslSocket.setNeedClientAuth(true);
            sslSocket.setEnabledProtocols(PROXY_TLS_PROTOCOLS);
            sslSocket.setEnabledCipherSuites(DEFAULT_XROAD_SSL_CIPHER_SUITES);
            return sslSocket;
        }

        private static void closeQuietly(Closeable closeable) {
            try {
                closeable.close();
            } catch (IOException ignored) {
                // best effort
            }
        }
    }

    /**
     * Trust manager that rejects every client certificate, triggering a post-handshake
     * TLS 1.3 authentication failure on the connecting client. The rejection is deferred —
     * first until the client proxy has finished establishing the connection, then until the
     * request stream has gone quiet — so that, like over a real network, the rejection alert
     * arrives while the client is executing the request: either parked reading the response
     * (the failure then surfaces as a handshake error on the read) or wedged writing a request
     * larger than the transport buffers (the failure then kills the write with a plain
     * connection error — the mid-write case the retry deliberately does not handle).
     */
    @Slf4j
    static final class RejectingClientTrustManager implements X509TrustManager {

        private static final long QUIET_PERIOD_NANOS = TimeUnit.SECONDS.toNanos(2);
        private static final long MAX_QUIET_WAIT_NANOS = TimeUnit.SECONDS.toNanos(15);
        private static final long POLL_INTERVAL_MILLIS = 20;

        private final HandshakeOrderGate gate;
        private final Socket plainSocket;

        RejectingClientTrustManager(HandshakeOrderGate gate, Socket plainSocket) {
            this.gate = gate;
            this.plainSocket = plainSocket;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            try {
                gate.awaitClientPastConnect();
                awaitRequestStreamQuiet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            throw new CertificateException("Client certificate rejected");
        }

        /**
         * Waits until no new bytes have arrived from the client for {@link #QUIET_PERIOD_NANOS}.
         * While this trust manager blocks, the TLS layer reads nothing from the plain socket,
         * so its unread byte count reflects everything the client has sent so far.
         */
        private void awaitRequestStreamQuiet() throws InterruptedException {
            try {
                InputStream in = plainSocket.getInputStream();
                long deadline = System.nanoTime() + MAX_QUIET_WAIT_NANOS;
                int unreadBytes = in.available();
                long quietSince = System.nanoTime();
                while (System.nanoTime() < deadline) {
                    TimeUnit.MILLISECONDS.sleep(POLL_INTERVAL_MILLIS);
                    int nowUnread = in.available();
                    if (nowUnread != unreadBytes) {
                        unreadBytes = nowUnread;
                        quietSince = System.nanoTime();
                    } else if (System.nanoTime() - quietSince >= QUIET_PERIOD_NANOS) {
                        return;
                    }
                }
                log.warn("The request stream never went quiet; rejecting anyway");
            } catch (IOException e) {
                log.trace("The connection broke while waiting for the request stream to go quiet", e);
            }
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[]{getCaCert()};
        }
    }

    /**
     * Service address resolver returning a scripted list of addresses per resolve call;
     * the last list repeats once the script is exhausted.
     */
    public static final class TestAddressResolver implements ServiceAddressResolver {
        private volatile List<List<URI>> plan = List.of();
        private final AtomicInteger calls = new AtomicInteger();

        /** Scripts the address lists to return for consecutive resolve calls. */
        public void plan(List<List<URI>> addressesPerResolve) {
            this.plan = addressesPerResolve;
            calls.set(0);
        }

        /** Returns the number of resolve calls made since the last plan/reset. */
        public int resolveCount() {
            return calls.get();
        }

        /** Clears the scripted plan and the call counter. */
        public void reset() {
            plan = List.of();
            calls.set(0);
        }

        @Override
        public List<URI> resolve(ServiceId serviceProvider, SecurityServerId securityServerId, ProxyRequestContext ctx) {
            int index = calls.getAndIncrement();
            return new ArrayList<>(plan.get(Math.min(index, plan.size() - 1)));
        }
    }

    /**
     * Message log manager that records log messages so tests can assert how many
     * client-side request records were produced.
     */
    public static final class CountingLogManager extends AbstractLogManager {
        private final List<LogMessage> messages = new CopyOnWriteArrayList<>();

        public CountingLogManager(GlobalConfProvider globalConfProvider, ServerConfProvider serverConfProvider) {
            super(globalConfProvider, serverConfProvider);
        }

        @Override
        public void log(LogMessage message) {
            messages.add(message);
        }

        @Override
        public TimestampRecord timestamp(Long messageRecordId) {
            return null;
        }

        @Override
        public Map<String, DiagnosticsStatus> getDiagnosticStatus() {
            return Map.of();
        }

        /** Returns the number of client-side request records logged (REST and SOAP). */
        public long clientRequestLogCount() {
            return messages.stream()
                    .filter(LogMessage::isClientSide)
                    .filter(message -> !isResponse(message))
                    .count();
        }

        /** Clears the recorded log messages. */
        public void reset() {
            messages.clear();
        }

        private static boolean isResponse(LogMessage message) {
            return switch (message) {
                case RestLogMessage rest -> rest.isResponse();
                case SoapLogMessage soap -> soap.isResponse();
            };
        }
    }
}
