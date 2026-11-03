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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.niis.xroad.edc.extension.jetty.ConnectorTlsTestSupport.ScriptedLoader;
import org.niis.xroad.edc.reload.PeriodicMaterialReloader.Loaded;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.edc.extension.jetty.ConnectorTlsTestSupport.unusable;
import static org.niis.xroad.edc.extension.jetty.ConnectorTlsTestSupport.usable;

class XRoadJettyExtensionTest {

    private static final String RELOAD_THREAD = "xrd-edc-reload-ds-https-keystore";
    private static final Duration PENDING_POLL = Duration.ofMillis(50);
    private static final Duration AWAIT = Duration.ofSeconds(10);

    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private final List<String> severes = new CopyOnWriteArrayList<>();
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
    private final ScriptedLoader loader = new ScriptedLoader();
    private final ServiceExtensionContext context = mock(ServiceExtensionContext.class);
    private final HealthCheckService healthCheckService = mock(HealthCheckService.class);

    private XRoadJettyExtension extension;
    private int port;

    @AfterEach
    void tearDown() {
        if (extension != null) {
            extension.shutdown();
        }
    }

    @Test
    void initializeWithAnEmptySlotReturnsAndLogsOneWarning() {
        extension = newExtension(60);

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> extension.initialize(context));

        assertThat(warnings).hasSize(1);
        assertThat(severes).isEmpty();
    }

    @Test
    void anEmptySlotLogsOneWarningOverBootAndTheFirstPendingCycle() {
        extension = newExtension(60);
        extension.initialize(context);
        extension.start();

        await().atMost(AWAIT).until(() -> loader.loads.get() >= 3);

        assertThat(warnings).hasSize(1);
        assertThat(severes).isEmpty();
    }

    @Test
    void initializeWithStoredMaterialLogsNoWarning() throws Exception {
        loader.serve(usable("internal", TestCertUtil.getInternalKey()));
        extension = newExtension(60);

        extension.initialize(context);

        assertThat(warnings).isEmpty();
    }

    @Test
    void readinessIsDownWhileTheSlotIsEmptyAndUpOnceTheListenerOpens() throws Exception {
        extension = newExtension(60);
        extension.initialize(context);
        extension.start();
        var readiness = readinessProvider();
        assertThat(readiness.get().succeeded()).isFalse();

        loader.serve(usable("internal", TestCertUtil.getInternalKey()));

        await().atMost(AWAIT).untilAsserted(() -> assertThat(readiness.get().succeeded()).isTrue());
    }

    @Test
    void materialArrivingInAnEmptySlotIsServedOverATlsHandshakeThroughTheExtension() throws Exception {
        extension = newExtension(60);
        extension.initialize(context);
        extension.start();
        assertThat(connectionRefused()).isTrue();

        loader.serve(usable("internal", TestCertUtil.getInternalKey()));

        await().atMost(AWAIT).untilAsserted(() ->
                assertThat(servedCertificate()).isEqualTo(TestCertUtil.getInternalKey().certChain[0]));
    }

    @Test
    void shutdownClosesTheReloaderStartedByStart() {
        extension = newExtension(60);
        extension.initialize(context);
        extension.start();
        assertThat(reloaderThreadAlive()).isTrue();

        extension.shutdown();

        await().atMost(Duration.ofSeconds(5)).until(() -> !reloaderThreadAlive());
    }

    @Test
    void aPortThatCannotBeBoundIsWarnedOnceNamingThePortNotTheCertificate() throws Exception {
        loader.serve(usable("internal", TestCertUtil.getInternalKey()));
        extension = newExtension(60);
        extension.initialize(context);
        try (var occupied = new ServerSocket(port)) {
            extension.start();
            await().atMost(AWAIT).until(() -> loader.loads.get() >= 4);
        }

        assertThat(warnings).hasSize(1);
        assertThat(warnings.getFirst()).contains("not bindable").contains("port " + port).doesNotContain("unusable");
        assertThat(severes).isEmpty();
    }

    @Test
    void theListenerOpensOnceThePortThatCouldNotBeBoundIsFree() throws Exception {
        loader.serve(usable("internal", TestCertUtil.getInternalKey()));
        extension = newExtension(60);
        extension.initialize(context);
        try (var occupied = new ServerSocket(port)) {
            extension.start();
            await().atMost(AWAIT).until(() -> loader.loads.get() >= 3);
        }

        await().atMost(AWAIT).untilAsserted(() -> new Socket("localhost", port).close());
    }

    @Test
    void anSslContextThatCannotBeBuiltIsWarnedOnce() throws Exception {
        loader.serve(unusable("broken"));
        extension = newExtension(60);
        extension.initialize(context);
        extension.start();

        await().atMost(AWAIT).until(() -> loader.loads.get() >= 4);

        assertThat(warnings).hasSize(1);
        assertThat(warnings.getFirst()).contains("could not be applied yet").contains("Cannot read the connector TLS entry");
        assertThat(severes).isEmpty();
    }

    @Test
    void anUnexpectedExceptionIsWarnedOnceWithItsMessage() throws Exception {
        loader.serve(usable("internal", TestCertUtil.getInternalKey()));
        extension = newExtension(60);
        extension.initialize(context);
        capturedJettyService().addConnectorConfigurationCallback(connector -> {
            throw new IllegalStateException("callback exploded");
        });
        extension.start();

        await().atMost(AWAIT).until(() -> loader.loads.get() >= 4);

        assertThat(warnings).hasSize(1);
        assertThat(warnings.getFirst()).contains("callback exploded");
        assertThat(severes).isEmpty();
    }

    @Test
    void twoDifferentFailuresWhilePendingAreWarnedTwiceAndARepeatOfTheSameOneIsNot() throws Exception {
        loader.serve(unusable("first"));
        extension = newExtension(60);
        extension.initialize(context);
        extension.start();
        await().atMost(AWAIT).until(() -> warnings.size() == 1);

        loader.serve(unusable("second"));
        await().atMost(AWAIT).until(() -> loader.loads.get() >= 6);

        assertThat(warnings).hasSize(1);

        loader.serve(mismatched("third"));

        await().atMost(AWAIT).until(() -> warnings.size() == 2);
        assertThat(warnings.getFirst()).contains("Cannot read the connector TLS entry");
        assertThat(warnings.getLast()).contains("does not match");
        assertThat(severes).isEmpty();
    }

    @Test
    void afterServingARejectionIsLoggedOnlyByTheServiceAsOneSevereLine() throws Exception {
        loader.serve(unusable("flaky"));
        extension = newExtension(1);
        extension.initialize(context);
        extension.start();
        await().atMost(AWAIT).until(() -> warnings.size() == 1);
        loader.serve(usable("good", TestCertUtil.getInternalKey()));
        var readiness = readinessProvider();
        await().atMost(AWAIT).untilAsserted(() -> assertThat(readiness.get().succeeded()).isTrue());
        var loadsBeforeRejection = loader.loads.get();

        loader.serve(unusable("flaky"));
        await().atMost(AWAIT).until(() -> loader.loads.get() >= loadsBeforeRejection + 4);

        assertThat(severes).hasSize(1);
        assertThat(severes.getFirst()).contains("Still serving");
        assertThat(warnings).hasSize(1);
    }

    private XRoadJettyExtension newExtension(long reloadIntervalSeconds) {
        when(context.getMonitor()).thenReturn(monitor);
        port = TestPortUtils.findRandomPort();
        return new XRoadJettyExtension(healthCheckService, loader, port, "/api", reloadIntervalSeconds, PENDING_POLL);
    }

    private ReadinessProvider readinessProvider() {
        var captor = ArgumentCaptor.forClass(ReadinessProvider.class);
        verify(healthCheckService).addReadinessProvider(captor.capture());
        return captor.getValue();
    }

    private XRoadJettyService capturedJettyService() {
        var captor = ArgumentCaptor.forClass(XRoadJettyService.class);
        verify(context).registerService(eq(XRoadJettyService.class), captor.capture());
        return captor.getValue();
    }

    private static Loaded<KeyStore> mismatched(String fingerprint) throws Exception {
        var keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry(InternalSSLKey.KEY_ALIAS, TestCertUtil.getOcspSigner().key, InternalSSLKey.getKEY_PASSWORD(),
                TestCertUtil.getInternalKey().certChain);
        return new Loaded<>(keyStore, fingerprint);
    }

    private boolean connectionRefused() {
        try (var socket = new Socket("localhost", port)) {
            return false;
        } catch (IOException _) {
            return true;
        }
    }

    private X509Certificate servedCertificate() throws Exception {
        return ConnectorTlsTestSupport.servedCertificate(port);
    }

    private static boolean reloaderThreadAlive() {
        return Thread.getAllStackTraces().keySet().stream()
                .anyMatch(thread -> RELOAD_THREAD.equals(thread.getName()) && thread.isAlive());
    }
}
