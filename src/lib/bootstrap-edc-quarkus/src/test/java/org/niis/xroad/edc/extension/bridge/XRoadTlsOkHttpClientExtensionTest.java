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
package org.niis.xroad.edc.extension.bridge;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.ApprovedConnectorTlsCAInfo;
import org.niis.xroad.globalconf.model.GlobalConfInitException;
import org.niis.xroad.globalconf.model.GlobalConfInitState;

import javax.net.SocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves the seam end to end: an {@link OkHttpClient} built by this extension makes a real HTTPS request that
 * succeeds against a peer chaining to a listed DS TLS CA, fails against one that does not, and — since an
 * unreadable globalconf must not abort boot — still gets built (rejecting everything) when globalconf throws.
 */
@ExtendWith(MockitoExtension.class)
class XRoadTlsOkHttpClientExtensionTest {

    private static final String INSTANCE_IDENTIFIER = "TEST";
    private static final String CLUSTER_HOST = "cluster.cs.test";
    private static final InetAddress DEAD_ADDRESS = InetAddress.ofLiteral("192.0.2.1");
    private static final InetAddress LIVE_ADDRESS = InetAddress.ofLiteral("127.0.0.1");
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration FAST_SKIP_LIMIT = CONNECT_TIMEOUT.minusSeconds(1);
    private static final String DEAD_CONNECT_CLOSED = "closed";
    private static final String DEAD_CONNECT_TIMED_OUT = "timed out";

    @Mock
    private ServiceExtensionContext context;

    @Mock
    private Monitor monitor;

    @Mock
    private GlobalConfProvider globalConfProvider;

    private HttpsServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void theBuiltClientAcceptsAPeerChainingToAListedCaAndRejectsOneThatDoesNot() throws Exception {
        var listedCa = selfSignedCa("Listed DS TLS CA");
        var unlistedCa = selfSignedCa("Unlisted CA");
        var acceptedServer = startHttpsServer(issueLeaf(listedCa, "accepted"));
        var rejectedServer = startHttpsServer(issueLeaf(unlistedCa, "rejected"));

        stubContext(List.of(connectorTlsCaInfo("listed", listedCa.certificate())));
        var client = new XRoadTlsOkHttpClientExtension().okHttpClient(context);

        assertThat(get(client, acceptedServer)).isEqualTo(200);
        assertThatThrownBy(() -> get(client, rejectedServer)).isInstanceOf(IOException.class);
    }

    @Test
    void anUnreadableGlobalConfAtBootStillProducesAClientThatRejectsEverything() throws Exception {
        var ca = selfSignedCa("Would-be Listed CA");
        var wouldBeAcceptedServer = startHttpsServer(issueLeaf(ca, "would-be-accepted"));

        when(globalConfProvider.getInstanceIdentifier()).thenThrow(new IllegalStateException("globalconf unreachable"));
        stubContextServices();

        var client = new XRoadTlsOkHttpClientExtension().okHttpClient(context);

        assertThat(client).isNotNull();
        assertThatThrownBy(() -> get(client, wouldBeAcceptedServer)).isInstanceOf(IOException.class);
    }

    @Test
    void globalConfThatIsNotInitializedAtBootIsWarnedAboutOnceWithoutAStackTrace() {
        when(globalConfProvider.getInstanceIdentifier()).thenThrow(new GlobalConfInitException(GlobalConfInitState.UNINITIALIZED));
        stubContextServices();
        var extension = new XRoadTlsOkHttpClientExtension();
        extension.okHttpClient(context);
        extension.shutdown();
        verify(monitor, times(1)).warning(anyString());
        verify(monitor, never()).severe(anyString(), any(Throwable[].class));
    }

    @Test
    void anyOtherFailureToLoadTheCaListAtBootIsStillSevereWithTheStackTrace() {
        var failure = new IllegalStateException("malformed globalconf");
        when(globalConfProvider.getInstanceIdentifier()).thenThrow(failure);
        stubContextServices();
        var extension = new XRoadTlsOkHttpClientExtension();
        extension.okHttpClient(context);
        extension.shutdown();
        verify(monitor).severe(anyString(), eq(failure));
    }

    @Test
    void aClientBuiltWhileGlobalConfIsUnreadableStartsAcceptingListedCasOnThePendingPollNotTheNormalInterval() throws Exception {
        var ca = selfSignedCa("Listed After Boot CA");
        var listedServer = startHttpsServer(issueLeaf(ca, "listed-after-boot"));
        when(globalConfProvider.getInstanceIdentifier())
                .thenThrow(new IllegalStateException("globalconf unreachable"))
                .thenReturn(INSTANCE_IDENTIFIER);
        when(globalConfProvider.getApprovedConnectorTlsCAs(INSTANCE_IDENTIFIER))
                .thenReturn(List.of(connectorTlsCaInfo("listed", ca.certificate())));
        stubContextServices();
        var extension = new XRoadTlsOkHttpClientExtension(Duration.ofSeconds(1));

        var client = extension.okHttpClient(context);

        assertThatThrownBy(() -> get(client, listedServer)).isInstanceOf(IOException.class);
        await().atMost(Duration.ofSeconds(10)).ignoreExceptions().untilAsserted(() -> assertThat(get(client, listedServer)).isEqualTo(200));
        extension.shutdown();
    }
    /**
     * The exact key and default are a contract other layers rely on without compiling against this class:
     * ansible/compose test-environment overrides (needed because CS-side globalconf registration only takes
     * effect after a reload cycle) set the YAML key {@code xroad.edc.web.https.trust.reload-interval-seconds}
     * directly, with nothing to catch a silent rename at compile time.
     */
    @Test
    void theReloadIntervalSettingKeyAndDefaultAreQueriedFromContext() throws Exception {
        var ca = selfSignedCa("Any CA");
        startHttpsServer(issueLeaf(ca, "any"));
        stubContext(List.of(connectorTlsCaInfo("any", ca.certificate())));

        new XRoadTlsOkHttpClientExtension().okHttpClient(context);

        verify(context).getSetting("xroad.edc.web.https.trust.reload-interval-seconds", 60L);
    }

    @Test
    void theBuiltClientDefaultsToTenSecondConnectAndReadTimeouts() throws Exception {
        var ca = selfSignedCa("Any CA");
        startHttpsServer(issueLeaf(ca, "any"));
        stubContext(List.of(connectorTlsCaInfo("any", ca.certificate())));

        var client = new XRoadTlsOkHttpClientExtension().okHttpClient(context);

        assertThat(client.connectTimeoutMillis()).isEqualTo(10_000);
        assertThat(client.readTimeoutMillis()).isEqualTo(10_000);
        verify(context).getSetting("edc.http.client.timeout.connect", 10L);
        verify(context).getSetting("edc.http.client.timeout.read", 10L);
    }

    @Test
    void theEdcClientTimeoutSettingsOverrideTheDefaults() throws Exception {
        var ca = selfSignedCa("Any CA");
        startHttpsServer(issueLeaf(ca, "any"));
        stubContext(List.of(connectorTlsCaInfo("any", ca.certificate())));
        stubConnectTimeout();
        lenient().when(context.getSetting(eq("edc.http.client.timeout.read"), anyLong()))
                .thenReturn(45L);

        var client = new XRoadTlsOkHttpClientExtension().okHttpClient(context);

        assertThat(client.connectTimeoutMillis()).isEqualTo(CONNECT_TIMEOUT.toMillis());
        assertThat(client.readTimeoutMillis()).isEqualTo(45_000);
    }

    @ParameterizedTest(name = "dead address first: {0}")
    @ValueSource(booleans = {true, false})
    void aDeadAddressOfAMultiAddressHostIsSkippedWellBeforeTheConnectTimeout(boolean deadAddressFirst) throws Exception {
        var ca = selfSignedCa("Any CA");
        var liveServer = startHttpsServer(issueLeaf(ca, "any", new GeneralName(GeneralName.dNSName, CLUSTER_HOST)));
        stubContext(List.of(connectorTlsCaInfo("any", ca.certificate())));
        stubConnectTimeout();
        var addresses = deadAddressFirst
                ? List.of(DEAD_ADDRESS, LIVE_ADDRESS)
                : List.of(LIVE_ADDRESS, DEAD_ADDRESS);
        var factory = new HangingConnectSocketFactory();
        var extension = new XRoadTlsOkHttpClientExtension();
        try {
            var client = extension.okHttpClient(context).newBuilder()
                    .proxy(Proxy.NO_PROXY)
                    .dns(_ -> addresses)
                    .socketFactory(factory)
                    .build();

            var started = System.nanoTime();
            var code = get(client, CLUSTER_HOST, liveServer);
            var elapsed = Duration.ofNanos(System.nanoTime() - started);

            assertThat(code).isEqualTo(200);
            assertThat(elapsed).isLessThan(FAST_SKIP_LIMIT);
            if (deadAddressFirst) {
                assertThat(factory.deadOutcome).succeedsWithin(CONNECT_TIMEOUT).isEqualTo(DEAD_CONNECT_CLOSED);
            } else {
                assertThat(factory.deadOutcome).isNotDone();
            }
        } finally {
            extension.shutdown();
        }
    }

    @Test
    void withNoConfiguredValueTheDispatcherUsesTheDefaultPerHostLimitAndMaxRequestsIsAtLeastThat() throws Exception {
        var ca = selfSignedCa("Any CA");
        startHttpsServer(issueLeaf(ca, "any"));
        stubContext(List.of(connectorTlsCaInfo("any", ca.certificate())));

        var client = new XRoadTlsOkHttpClientExtension().okHttpClient(context);

        assertThat(client.dispatcher().getMaxRequestsPerHost()).isEqualTo(8);
        assertThat(client.dispatcher().getMaxRequests()).isGreaterThanOrEqualTo(8);
    }

    @Test
    void aConfiguredValueReachesTheDispatcherAndMaxRequestsIsNeverBelowIt() throws Exception {
        var ca = selfSignedCa("Any CA");
        startHttpsServer(issueLeaf(ca, "any"));
        stubContext(List.of(connectorTlsCaInfo("any", ca.certificate())));
        lenient().when(context.getSetting("xroad.dsp.outbound-http.max-requests-per-host", 8)).thenReturn(200);

        var client = new XRoadTlsOkHttpClientExtension().okHttpClient(context);

        assertThat(client.dispatcher().getMaxRequestsPerHost()).isEqualTo(200);
        assertThat(client.dispatcher().getMaxRequests()).isGreaterThanOrEqualTo(200);
    }

    @Test
    void shutdownClosesTheReloaderWithoutThrowing() throws Exception {
        var ca = selfSignedCa("Any CA");
        startHttpsServer(issueLeaf(ca, "any"));
        stubContext(List.of(connectorTlsCaInfo("any", ca.certificate())));
        var extension = new XRoadTlsOkHttpClientExtension();
        extension.okHttpClient(context);

        assertThatCode(extension::shutdown).doesNotThrowAnyException();
    }

    private void stubContext(List<ApprovedConnectorTlsCAInfo> cas) {
        when(globalConfProvider.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);
        when(globalConfProvider.getApprovedConnectorTlsCAs(INSTANCE_IDENTIFIER)).thenReturn(cas);
        stubContextServices();
    }

    private void stubContextServices() {
        lenient().when(context.getMonitor()).thenReturn(monitor);
        lenient().when(context.getService(GlobalConfProvider.class)).thenReturn(globalConfProvider);
        // No setting configured in these tests: mirrors real behaviour (SettingResolver returns the
        // caller's own default) rather than Mockito's zero-value default, which would otherwise hand
        // PeriodicMaterialReloader.schedule a zero-second interval and blow up scheduleWithFixedDelay.
        lenient().when(context.getSetting(anyString(), anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        // Same reasoning as the anyLong() stub above, for the int-typed per-host limit setting.
        lenient().when(context.getSetting(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(1));
    }

    private void stubConnectTimeout() {
        lenient().when(context.getSetting(eq("edc.http.client.timeout.connect"), anyLong()))
                .thenReturn(CONNECT_TIMEOUT.toSeconds());
    }

    private static int get(OkHttpClient client, HttpsServer server) throws Exception {
        return get(client, LIVE_ADDRESS.getHostAddress(), server);
    }

    private static int get(OkHttpClient client, String host, HttpsServer server) throws Exception {
        var request = new Request.Builder().url(URI.create("https://" + host + ":" + server.getAddress().getPort() + "/").toURL()).build();
        try (var response = client.newCall(request).execute()) {
            return response.code();
        }
    }

    private static ApprovedConnectorTlsCAInfo connectorTlsCaInfo(String name, X509Certificate certificate) {
        return new ApprovedConnectorTlsCAInfo(name, certificate, List.of(), null, null, null);
    }

    private HttpsServer startHttpsServer(TestLeaf leaf) throws Exception {
        var keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("server", leaf.privateKey(), new char[0], leaf.chain());
        var keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, new char[0]);
        var sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), null, null);

        var httpsServer = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext));
        httpsServer.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        httpsServer.start();
        server = httpsServer;
        return httpsServer;
    }

    private static TestCa selfSignedCa(String commonName) throws Exception {
        var keyPair = generateKeyPair();
        var subject = new X500Name("CN=" + commonName);
        var builder = new JcaX509v3CertificateBuilder(subject, BigInteger.valueOf(System.nanoTime()), notBefore(), notAfter(), subject,
                keyPair.getPublic())
                .addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        var certificate = new JcaX509CertificateConverter().getCertificate(builder.build(signer));
        return new TestCa(keyPair, certificate);
    }

    private static TestLeaf issueLeaf(TestCa ca, String commonName) throws Exception {
        return issueLeaf(ca, commonName, new GeneralName(GeneralName.iPAddress, "127.0.0.1"));
    }

    private static TestLeaf issueLeaf(TestCa ca, String commonName, GeneralName subjectAlternativeName) throws Exception {
        var leafKeyPair = generateKeyPair();
        var issuer = new X500Name(ca.certificate().getSubjectX500Principal().getName());
        var subject = new X500Name("CN=" + commonName);
        // OkHttp verifies the hostname independently of the trust manager under test. The SAN must match the host
        // in the request URL: the IP address for 127.0.0.1, the DNS name for cluster.cs.test.
        var builder = new JcaX509v3CertificateBuilder(issuer, BigInteger.valueOf(System.nanoTime()), notBefore(), notAfter(), subject,
                leafKeyPair.getPublic())
                .addExtension(Extension.subjectAlternativeName, false, new GeneralNames(subjectAlternativeName));
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(ca.keyPair().getPrivate());
        var leafCertificate = new JcaX509CertificateConverter().getCertificate(builder.build(signer));
        return new TestLeaf(leafKeyPair.getPrivate(), new X509Certificate[] {leafCertificate, ca.certificate()});
    }

    private static KeyPair generateKeyPair() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static Date notBefore() {
        return Date.from(Instant.now().minus(1, ChronoUnit.DAYS));
    }

    private static Date notAfter() {
        return Date.from(Instant.now().plus(365, ChronoUnit.DAYS));
    }

    /**
     * A connect to {@code DEAD_ADDRESS} hangs until the connect timeout or until the socket is closed, and the
     * way it ends is recorded in {@code deadOutcome}. Connects to other addresses work normally.
     */
    private static final class HangingConnectSocketFactory extends SocketFactory {
        private final SocketFactory delegate = SocketFactory.getDefault();
        private final CompletableFuture<String> deadOutcome = new CompletableFuture<>();

        @Override
        public Socket createSocket() {
            return new Socket() {
                private final CountDownLatch closed = new CountDownLatch(1);

                @Override
                public void connect(SocketAddress endpoint, int timeoutMillis) throws IOException {
                    if (!(endpoint instanceof InetSocketAddress address) || !DEAD_ADDRESS.equals(address.getAddress())) {
                        super.connect(endpoint, timeoutMillis);
                        return;
                    }
                    try {
                        if (closed.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                            deadOutcome.complete(DEAD_CONNECT_CLOSED);
                            throw new SocketException("Socket closed");
                        }
                    } catch (InterruptedException _) {
                        Thread.currentThread().interrupt();
                        throw new SocketException("Interrupted");
                    }
                    deadOutcome.complete(DEAD_CONNECT_TIMED_OUT);
                    throw new SocketTimeoutException("connect timed out");
                }

                @Override
                public void close() throws IOException {
                    closed.countDown();
                    super.close();
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

    private record TestCa(KeyPair keyPair, X509Certificate certificate) {
    }

    private record TestLeaf(PrivateKey privateKey, X509Certificate[] chain) {
    }
}
