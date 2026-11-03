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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.edc.extension.jetty.ConnectorTlsTestSupport.ScriptedLoader;
import org.niis.xroad.edc.reload.PeriodicMaterialReloader.Loaded;
import org.niis.xroad.edc.reload.PeriodicMaterialReloader.MaterialLoader;

import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.edc.extension.jetty.ConnectorTlsTestSupport.keyStoreOf;
import static org.niis.xroad.edc.extension.jetty.ConnectorTlsTestSupport.wrongKeyPassword;

class ConnectorTlsFailedReloadTest {

    private static final String CONTINUING_TO_SERVE = "continuing to serve the previously loaded material";
    private static final Duration AWAIT = Duration.ofSeconds(30);

    private final List<String> severes = new CopyOnWriteArrayList<>();
    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private final Monitor monitor = new Monitor() {
        @Override
        public void warning(String message, Throwable... errors) {
            warnings.add(message);
        }

        @Override
        public void severe(String message, Throwable... errors) {
            severes.add(message);
        }
    };

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

    static Stream<Arguments> materialsJettyCannotLoad() throws Exception {
        var wrongPassword = wrongKeyPassword();
        var extraEntryWithOtherPassword = keyStoreOf(TestCertUtil.getOcspSigner());
        extraEntryWithOtherPassword.setKeyEntry("extra", TestCertUtil.getInternalKey().key, "other".toCharArray(),
                TestCertUtil.getInternalKey().certChain);
        return Stream.of(
                argumentSet("key entry with a wrong password", wrongPassword),
                argumentSet("extra entry with another password", extraEntryWithOtherPassword),
                argumentSet("uninitialized keystore", KeyStore.getInstance("PKCS12")));
    }

    @ParameterizedTest
    @MethodSource("materialsJettyCannotLoad")
    void materialThatJettyCannotLoadIsRejectedAndThePreviousCertificateKeepsBeingServed(KeyStore material)
            throws Exception {
        startService(keyStoreOf(TestCertUtil.getInternalKey()));

        assertThat(servedCertificate()).isEqualTo(TestCertUtil.getInternalKey().certChain[0]);
        assertThatThrownBy(() -> jettyService.applyConnectorTlsMaterial(material))
                .isInstanceOf(ConnectorTlsKeyStoreLoadException.class);
        assertThat(servedCertificate()).isEqualTo(TestCertUtil.getInternalKey().certChain[0]);
        assertThat(jettyService.isServingConnectorTls()).isTrue();
        assertThat(sharedSslContextFactory().getSslContext()).isNotNull();
    }

    @Test
    void aLaterUsableReloadIsServed() throws Exception {
        startService(keyStoreOf(TestCertUtil.getInternalKey()));

        assertThatThrownBy(() -> jettyService.applyConnectorTlsMaterial(wrongKeyPassword()))
                .isInstanceOf(ConnectorTlsKeyStoreLoadException.class);

        jettyService.applyConnectorTlsMaterial(keyStoreOf(TestCertUtil.getOcspSigner()));

        assertThat(servedCertificate()).isEqualTo(TestCertUtil.getOcspSigner().certChain[0]);
    }

    @Test
    void aKeyThatDoesNotMatchItsCertificateIsRejectedNamingBothCertificatesAndTheServedCertificateStays()
            throws Exception {
        var vaultClient = mock(VaultClient.class);
        var loader = new DsHttpsKeyStoreLoader(vaultClient);
        var good = TestCertUtil.getInternalKey();
        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(new InternalSSLKey(good.key, good.certChain));
        startService(loader.load().material());

        assertThat(servedCertificate()).isEqualTo(good.certChain[0]);

        when(vaultClient.getDsHttpsTlsCredentials())
                .thenReturn(new InternalSSLKey(TestCertUtil.getOcspSigner().key, good.certChain));
        var mismatched = loader.load().material();
        var served = good.certChain[0];

        assertThatThrownBy(() -> jettyService.applyConnectorTlsMaterial(mismatched))
                .isInstanceOf(ConnectorTlsKeyStoreLoadException.class)
                .hasMessageContaining("does not match")
                .hasMessageContaining(ConnectorTlsCertificates.fingerprintOrUnknown(mismatched))
                .hasMessageContaining(ConnectorTlsCertificates.describe(served));
        assertThat(servedCertificate()).isEqualTo(served);
        assertThat(jettyService.isServingConnectorTls()).isTrue();
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
        jettyService.start();

        assertThatThrownBy(() -> jettyService.applyConnectorTlsMaterial(mismatched))
                .isInstanceOf(ConnectorTlsKeyStoreLoadException.class)
                .hasMessageContaining("does not match");
        assertThat(jettyService.isServingConnectorTls()).isFalse();
        assertThat(jettyService.notServedCause()).hasValueSatisfying(cause -> assertThat(cause).contains("does not match"));
    }

    @Test
    void aRejectedRotationIsLoggedEveryCycleNamingBothCertificatesAndKeepsServing() throws Exception {
        var loader = new ScriptedLoader();
        loader.serve(new Loaded<>(keyStoreOf(TestCertUtil.getInternalKey()), "good"));
        var healthCheckService = startExtension(loader);
        var readiness = readinessProvider(healthCheckService);

        await().atMost(AWAIT).untilAsserted(() -> assertThat(readiness.get().succeeded()).isTrue());
        var served = TestCertUtil.getInternalKey().certChain[0];
        assertThat(servedCertificate()).isEqualTo(served);

        var rotated = wrongKeyPassword();
        loader.serve(new Loaded<>(rotated, "rotated-unusable"));
        await().atMost(AWAIT).until(() -> severes.stream().anyMatch(message -> message.contains(CONTINUING_TO_SERVE)));

        var attemptLines = warnings.stream().filter(message -> message.contains("reload attempt")).toList();
        assertThat(attemptLines).hasSizeGreaterThanOrEqualTo(3)
                .allSatisfy(message -> assertThat(message)
                        .contains(ConnectorTlsCertificates.fingerprintOrUnknown(rotated))
                        .contains(ConnectorTlsCertificates.describe(served)));
        assertThat(attemptLines.getFirst()).contains("reload attempt 1/3 failed");
        assertThat(servedCertificate()).isEqualTo(served);
        assertThat(readiness.get().succeeded()).isTrue();
    }

    @Test
    void aRotationTheLoaderRejectsKeepsServingThePreviousCertificate() throws Exception {
        var loader = new ScriptedLoader();
        loader.serve(new Loaded<>(keyStoreOf(TestCertUtil.getInternalKey()), "good"));
        var healthCheckService = startExtension(loader);
        var readiness = readinessProvider(healthCheckService);

        await().atMost(AWAIT).untilAsserted(() -> assertThat(readiness.get().succeeded()).isTrue());

        loader.fail(new ConnectorTlsKeyStoreLoadException("Failed to build the connector TLS keystore"));

        await().atMost(AWAIT).until(() -> severes.stream().anyMatch(message -> message.contains(CONTINUING_TO_SERVE)));
        assertThat(servedCertificate()).isEqualTo(TestCertUtil.getInternalKey().certChain[0]);
        assertThat(readiness.get().succeeded()).isTrue();
    }

    @Test
    void anEmptyOpenBaoSlotKeepsTheServiceNotReadyAndSaysSo() throws Exception {
        var vaultClient = mock(VaultClient.class);
        when(vaultClient.getDsHttpsTlsCredentials()).thenThrow(XrdRuntimeException.systemException(ErrorCode.MISSING_SECRET)
                .details("Secret not found at path: tls/ds-https")
                .build());
        var healthCheckService = startExtension(new DsHttpsKeyStoreLoader(vaultClient));
        var result = readinessProvider(healthCheckService).get();
        assertThat(result.succeeded()).isFalse();
        assertThat(result.getFailureDetail()).contains("slot empty");
    }

    private void startService(KeyStore initial) {
        port = TestPortUtils.findRandomPort();
        var registry = new XRoadPortMappingRegistry();
        registry.register(new PortMapping("svc", port, "/api"));
        jettyService = new XRoadJettyService(monitor, registry);
        jettyService.addConnectorConfigurationCallback(connectors::add);
        jettyService.start();
        jettyService.applyConnectorTlsMaterial(initial);
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

    private X509Certificate servedCertificate() throws Exception {
        return ConnectorTlsTestSupport.servedCertificate(port);
    }
}
