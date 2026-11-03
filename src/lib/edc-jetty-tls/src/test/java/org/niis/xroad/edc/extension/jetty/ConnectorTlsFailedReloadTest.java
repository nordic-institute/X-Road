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
package org.niis.xroad.edc.extension.jetty;

import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.TestPortUtils;
import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.util.CryptoUtils;

import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.health.HealthCheckService;
import org.eclipse.edc.spi.system.health.ReadinessProvider;
import org.eclipse.edc.web.spi.configuration.PortMapping;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.edc.reload.PeriodicMaterialReloader.Loaded;
import org.niis.xroad.edc.reload.PeriodicMaterialReloader.MaterialLoader;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectorTlsFailedReloadTest {

    private static final String CONTINUING_TO_SERVE = "continuing to serve the previously loaded material";
    private static final Duration AWAIT = Duration.ofSeconds(30);

    private final List<String> severes = new CopyOnWriteArrayList<>();
    private final Monitor monitor = new Monitor() {
        @Override
        public void severe(String message, Throwable... errors) {
            severes.add(message);
        }
    };

    private final MutableClock clock = new MutableClock(Instant.now());

    private XRoadJettyService jettyService;
    private XRoadJettyExtension extension;
    private final List<ServerConnector> connectors = new CopyOnWriteArrayList<>();
    private int port;

    @AfterEach
    void tearDown() {
        if (extension != null) {
            extension.shutdown();
        }
        if (jettyService != null) {
            jettyService.shutdown();
        }
    }

    @Test
    void aReloadThatJettyCannotLoadKeepsServingThePreviousCertificate() throws Exception {
        startService(keyStoreOf(TestCertUtil.getInternalKey(), InternalSSLKey.getKEY_PASSWORD()));

        assertThat(servedCertificate()).isEqualTo(TestCertUtil.getInternalKey().certChain[0]);
        assertThatThrownBy(() -> jettyService.applyConnectorTlsMaterial(wrongKeyPassword()))
                .isInstanceOf(ConnectorTlsMaterialRejectedException.class);
        assertThat(servedCertificate()).isEqualTo(TestCertUtil.getInternalKey().certChain[0]);
        assertThat(jettyService.isServingConnectorTls()).isTrue();
        assertThat(sharedSslContextFactory().getSslContext()).isNotNull();
        assertThat(jettyService.rejection()).isPresent();
    }

    @Test
    void aKeyStoreThatOnlyJettyCannotLoadIsRejectedByTheThrowawayLoadBeforeTheLiveFactoryIsTouched() throws Exception {
        startService(keyStoreOf(TestCertUtil.getInternalKey(), InternalSSLKey.getKEY_PASSWORD()));
        var extraEntryWithOtherPassword = keyStoreOf(TestCertUtil.getOcspSigner(), InternalSSLKey.getKEY_PASSWORD());
        extraEntryWithOtherPassword.setKeyEntry("extra", TestCertUtil.getInternalKey().key, "other".toCharArray(),
                TestCertUtil.getInternalKey().certChain);

        assertThatThrownBy(() -> jettyService.applyConnectorTlsMaterial(extraEntryWithOtherPassword))
                .isInstanceOf(ConnectorTlsMaterialRejectedException.class)
                .hasMessageContaining("cannot be loaded by Jetty");
        assertThat(servedCertificate()).isEqualTo(TestCertUtil.getInternalKey().certChain[0]);
        assertThat(sharedSslContextFactory().getSslContext()).isNotNull();
    }

    @Test
    void anUninitializedKeyStoreIsRejectedAndTheServedCertificateStays() throws Exception {
        startService(keyStoreOf(TestCertUtil.getInternalKey(), InternalSSLKey.getKEY_PASSWORD()));

        assertThatThrownBy(() -> jettyService.applyConnectorTlsMaterial(KeyStore.getInstance("PKCS12")))
                .isInstanceOf(ConnectorTlsMaterialRejectedException.class);
        assertThat(servedCertificate()).isEqualTo(TestCertUtil.getInternalKey().certChain[0]);
        assertThat(jettyService.isServingConnectorTls()).isTrue();
        assertThat(jettyService.rejection()).hasValueSatisfying(rejection -> assertThat(rejection.fingerprint()).isEqualTo("unknown"));
    }

    @Test
    void aLaterUsableReloadServesTheNewCertificateAndClearsTheRejection() throws Exception {
        startService(keyStoreOf(TestCertUtil.getInternalKey(), InternalSSLKey.getKEY_PASSWORD()));

        assertThatThrownBy(() -> jettyService.applyConnectorTlsMaterial(wrongKeyPassword()))
                .isInstanceOf(ConnectorTlsMaterialRejectedException.class);
        assertThat(jettyService.rejection()).isPresent();

        jettyService.applyConnectorTlsMaterial(keyStoreOf(TestCertUtil.getOcspSigner(), InternalSSLKey.getKEY_PASSWORD()));

        assertThat(servedCertificate()).isEqualTo(TestCertUtil.getOcspSigner().certChain[0]);
        assertThat(jettyService.rejection()).isEmpty();
        assertThat(new ConnectorTlsReadinessProvider(jettyService).readinessDetail()).isEmpty();
    }

    @Test
    void aKeyThatDoesNotMatchItsCertificateIsRejectedAndTheServedCertificateStays() throws Exception {
        var vaultClient = mock(VaultClient.class);
        var loader = new DsHttpsKeyStoreLoader(vaultClient);
        var good = TestCertUtil.getInternalKey();
        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(new InternalSSLKey(good.key, good.certChain));
        startService(loader.load().material());

        assertThat(servedCertificate()).isEqualTo(good.certChain[0]);

        when(vaultClient.getDsHttpsTlsCredentials())
                .thenReturn(new InternalSSLKey(TestCertUtil.getOcspSigner().key, good.certChain));
        var mismatched = loader.load();

        assertThatThrownBy(() -> jettyService.applyConnectorTlsMaterial(mismatched.material()))
                .isInstanceOf(ConnectorTlsMaterialRejectedException.class)
                .hasMessageContaining("does not match");
        assertThat(servedCertificate()).isEqualTo(good.certChain[0]);
        assertThat(jettyService.isServingConnectorTls()).isTrue();
        assertThat(severes).hasSize(1);
        assertThat(severes.getFirst()).contains("does not match").contains("Still serving").contains("SHA-256");
    }

    @Test
    void aKeyThatDoesNotMatchItsCertificateIsRefusedOnTheFirstLoadToo() throws Exception {
        var good = TestCertUtil.getInternalKey();
        var mismatched = KeyStore.getInstance("PKCS12");
        mismatched.load(null, null);
        mismatched.setKeyEntry(InternalSSLKey.KEY_ALIAS, TestCertUtil.getOcspSigner().key, InternalSSLKey.getKEY_PASSWORD(),
                good.certChain);
        var registry = new XRoadPortMappingRegistry();
        registry.register(new PortMapping("svc", TestPortUtils.findRandomPort(), "/api"));
        jettyService = new XRoadJettyService(monitor, registry);

        assertThatThrownBy(() -> jettyService.applyConnectorTlsMaterial(mismatched))
                .isInstanceOf(ConnectorTlsKeyStoreLoadException.class)
                .isNotInstanceOf(ConnectorTlsMaterialRejectedException.class)
                .hasMessageContaining("does not match");
        assertThat(jettyService.rejection()).isEmpty();
    }

    @Test
    void theLoaderRejectsAChainWhoseCertificatesDoNotLinkBeforeJettySeesIt() throws Exception {
        var vaultClient = mock(VaultClient.class);
        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(new InternalSSLKey(TestCertUtil.getInternalKey().key,
                new X509Certificate[] {TestCertUtil.getInternalKey().certChain[0], TestCertUtil.getOcspSigner().certChain[0]}));

        assertThatThrownBy(() -> new DsHttpsKeyStoreLoader(vaultClient).load())
                .isInstanceOf(ConnectorTlsKeyStoreLoadException.class)
                .hasMessageContaining("Failed to build the connector TLS keystore");
    }

    @Test
    void corruptPemMaterialFailsWhileParsingBeforeTheLoaderBuildsAKeyStore() {
        var corrupt = "-----BEGIN PRIVATE KEY-----\nbm90IGEga2V5\n-----END PRIVATE KEY-----\n";

        assertThatThrownBy(() -> CryptoUtils.getPrivateKey(new ByteArrayInputStream(corrupt.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(Exception.class);
    }

    @Test
    void aSevereLineNamesTheServedCertificateOncePerCauseAndRemindsHourlyWhileTheRejectionPersists() throws Exception {
        startService(keyStoreOf(TestCertUtil.getInternalKey(), InternalSSLKey.getKEY_PASSWORD()));
        var bad = wrongKeyPassword();
        rejectTimes(bad, 3);

        assertThat(severes).hasSize(1);
        assertThat(severes.getFirst()).contains("rejected").contains("Still serving").contains("SHA-256").contains("expires");

        clock.advance(Duration.ofMinutes(59));
        rejectTimes(bad, 1);

        assertThat(severes).hasSize(1);

        clock.advance(Duration.ofMinutes(2));
        rejectTimes(bad, 3);

        assertThat(severes).hasSize(2);
        assertThat(severes.getLast()).contains("still rejected since");
    }

    @Test
    void noReminderAfterAGoodReloadClearedTheRejectionAndANewRejectionIsReportedAsNew() throws Exception {
        startService(keyStoreOf(TestCertUtil.getInternalKey(), InternalSSLKey.getKEY_PASSWORD()));
        var bad = wrongKeyPassword();
        rejectTimes(bad, 1);
        jettyService.applyConnectorTlsMaterial(keyStoreOf(TestCertUtil.getOcspSigner(), InternalSSLKey.getKEY_PASSWORD()));
        clock.advance(Duration.ofHours(3));

        assertThat(severes).hasSize(1);

        rejectTimes(bad, 2);

        assertThat(severes).hasSize(2);
        assertThat(severes.getLast()).doesNotContain("still rejected since");
    }

    @Test
    void aFailedHotReloadLogsContinuingToServeOnlyWhileThePreviousCertificateIsStillServed() throws Exception {
        var loader = new ScriptedLoader();
        loader.serve(new Loaded<>(keyStoreOf(TestCertUtil.getInternalKey(), InternalSSLKey.getKEY_PASSWORD()), "good"));
        var healthCheckService = startExtension(loader);
        var readiness = readinessProvider(healthCheckService);

        await().atMost(AWAIT).untilAsserted(() -> assertThat(readiness.get().succeeded()).isTrue());
        assertThat(servedCertificate()).isEqualTo(TestCertUtil.getInternalKey().certChain[0]);

        loader.serve(new Loaded<>(wrongKeyPassword(), "rotated-unusable"));

        await().atMost(AWAIT).until(() -> severes.stream().anyMatch(message -> message.contains(CONTINUING_TO_SERVE)));
        assertThat(servedCertificate()).isEqualTo(TestCertUtil.getInternalKey().certChain[0]);
        assertThat(readiness.get().succeeded()).isTrue();
    }

    @Test
    void aRotationTheLoaderRejectsKeepsServingThePreviousCertificate() throws Exception {
        var loader = new ScriptedLoader();
        loader.serve(new Loaded<>(keyStoreOf(TestCertUtil.getInternalKey(), InternalSSLKey.getKEY_PASSWORD()), "good"));
        var healthCheckService = startExtension(loader);
        var readiness = readinessProvider(healthCheckService);

        await().atMost(AWAIT).untilAsserted(() -> assertThat(readiness.get().succeeded()).isTrue());

        loader.fail(new ConnectorTlsKeyStoreLoadException("Failed to build the connector TLS keystore"));

        await().atMost(AWAIT).until(() -> severes.stream().anyMatch(message -> message.contains(CONTINUING_TO_SERVE)));
        assertThat(servedCertificate()).isEqualTo(TestCertUtil.getInternalKey().certChain[0]);
        assertThat(readiness.get().succeeded()).isTrue();
    }

    private void rejectTimes(KeyStore keyStore, int times) {
        for (var i = 0; i < times; i++) {
            assertThatThrownBy(() -> jettyService.applyConnectorTlsMaterial(keyStore))
                    .isInstanceOf(ConnectorTlsMaterialRejectedException.class);
        }
    }
    private void startService(KeyStore initial) {
        port = TestPortUtils.findRandomPort();
        var registry = new XRoadPortMappingRegistry();
        registry.register(new PortMapping("svc", port, "/api"));
        jettyService = new XRoadJettyService(monitor, registry, clock);
        jettyService.addConnectorConfigurationCallback(connectors::add);
        jettyService.applyConnectorTlsMaterial(initial);
        jettyService.start();
    }

    private SslContextFactory.Server sharedSslContextFactory() {
        return (SslContextFactory.Server) connectors.getFirst().getConnectionFactory(SslConnectionFactory.class)
                .getSslContextFactory();
    }

    private HealthCheckService startExtension(MaterialLoader<KeyStore> loader) {
        var context = mock(ServiceExtensionContext.class);
        when(context.getMonitor()).thenReturn(monitor);
        var healthCheckService = mock(HealthCheckService.class);
        port = TestPortUtils.findRandomPort();
        extension = new XRoadJettyExtension(healthCheckService, loader, port, "/api", 1, Duration.ofMillis(50));
        extension.initialize(context);
        extension.start();
        return healthCheckService;
    }

    private static ReadinessProvider readinessProvider(HealthCheckService healthCheckService) {
        var captor = ArgumentCaptor.forClass(ReadinessProvider.class);
        verify(healthCheckService).addReadinessProvider(captor.capture());
        return captor.getValue();
    }

    private static KeyStore keyStoreOf(TestCertUtil.PKCS12 credentials, char[] keyPassword) throws Exception {
        var keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry(InternalSSLKey.KEY_ALIAS, credentials.key, keyPassword, credentials.certChain);
        return keyStore;
    }

    private static KeyStore wrongKeyPassword() throws Exception {
        return keyStoreOf(TestCertUtil.getOcspSigner(), "not-the-keystore-password".toCharArray());
    }

    private boolean portAcceptsTcp() {
        try (var socket = new Socket("localhost", port)) {
            return socket.isConnected();
        } catch (IOException _) {
            return false;
        }
    }

    private X509Certificate servedCertificate() throws Exception {
        var trustAll = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
                // trust-all test client: every certificate is accepted
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
                // trust-all test client: every certificate is accepted
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
        var sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[] {trustAll}, new SecureRandom());
        try (var socket = (SSLSocket) sslContext.getSocketFactory().createSocket("localhost", port)) {
            socket.setSoTimeout((int) Duration.ofSeconds(5).toMillis());
            socket.startHandshake();
            return (X509Certificate) socket.getSession().getPeerCertificates()[0];
        }
    }

    private static final class ScriptedLoader implements MaterialLoader<KeyStore> {

        private final AtomicReference<Loaded<KeyStore>> current = new AtomicReference<>();
        private final AtomicReference<RuntimeException> failure = new AtomicReference<>();
        private final AtomicInteger loads = new AtomicInteger();

        void serve(Loaded<KeyStore> material) {
            failure.set(null);
            current.set(material);
        }

        void fail(RuntimeException exception) {
            failure.set(exception);
        }

        @Override
        public Loaded<KeyStore> load() {
            loads.incrementAndGet();
            var exception = failure.get();
            if (exception != null) {
                throw exception;
            }
            return current.get();
        }
    }
}
