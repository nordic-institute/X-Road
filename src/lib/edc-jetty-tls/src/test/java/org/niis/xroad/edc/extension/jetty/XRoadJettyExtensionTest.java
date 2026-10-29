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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.vault.VaultClient;

import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class XRoadJettyExtensionTest {

    private static final String RELOAD_THREAD = "xrd-edc-reload-ds-https-keystore";

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
    private final VaultClient vaultClient = mock(VaultClient.class);
    private final ServiceExtensionContext context = mock(ServiceExtensionContext.class);

    private XRoadJettyExtension extension;
    private int port;

    @AfterEach
    void tearDown() {
        if (extension != null) {
            extension.shutdown();
        }
    }

    @Test
    void initializeWithAnEmptySlotReturnsAndLogsOneWarning() throws Exception {
        when(vaultClient.getDsHttpsTlsCredentials()).thenThrow(new IllegalStateException("slot is empty"));
        extension = newExtension();

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> extension.initialize(context));

        assertThat(warnings).hasSize(1);
        assertThat(severes).isEmpty();
    }

    @Test
    void initializeWithStoredMaterialLogsNoWarning() throws Exception {
        var credentials = TestCertUtil.getInternalKey();
        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(new InternalSSLKey(credentials.key, credentials.certChain));
        extension = newExtension();

        extension.initialize(context);

        assertThat(warnings).isEmpty();
    }

    @Test
    void shutdownClosesTheReloaderStartedByStart() throws Exception {
        when(vaultClient.getDsHttpsTlsCredentials()).thenThrow(new IllegalStateException("slot is empty"));
        extension = newExtension();
        extension.initialize(context);
        extension.start();
        assertThat(reloaderThreadAlive()).isTrue();

        extension.shutdown();

        await().atMost(Duration.ofSeconds(5)).until(() -> !reloaderThreadAlive());
    }

    @Test
    void materialThatLoadsButCannotBeAppliedIsReportedOnceAsUnusableNotAsNotAvailable() throws Exception {
        givenStoredMaterial();
        extension = newExtension();
        setField(extension, "pendingPollInterval", Duration.ofMillis(50));
        extension.initialize(context);
        try (var occupied = new ServerSocket(port)) {
            extension.start();
            verify(vaultClient, timeout(5000).atLeast(4)).getDsHttpsTlsCredentials();
        }
        assertThat(severes).hasSize(1);
        assertThat(severes.getFirst()).contains("connector TLS certificate present but unusable");
        assertThat(warnings).isEmpty();
    }
    @Test
    void theListenerOpensOnceTheMaterialThatCouldNotBeAppliedBecomesUsable() throws Exception {
        givenStoredMaterial();
        extension = newExtension();
        setField(extension, "pendingPollInterval", Duration.ofMillis(50));
        extension.initialize(context);
        try (var occupied = new ServerSocket(port)) {
            extension.start();
            verify(vaultClient, timeout(5000).atLeast(3)).getDsHttpsTlsCredentials();
        }
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> new Socket("localhost", port).close());
    }
    private void givenStoredMaterial() throws Exception {
        var credentials = TestCertUtil.getInternalKey();
        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(new InternalSSLKey(credentials.key, credentials.certChain));
    }
    private XRoadJettyExtension newExtension() throws Exception {
        when(context.getMonitor()).thenReturn(monitor);
        var created = new XRoadJettyExtension();
        port = TestPortUtils.findRandomPort();
        setField(created, "defaultPort", port);
        setField(created, "defaultPath", "/api");
        setField(created, "reloadIntervalSeconds", 60L);
        setField(created, "vaultClient", vaultClient);
        return created;
    }

    private static void setField(XRoadJettyExtension target, String name, Object value) throws Exception {
        var field = XRoadJettyExtension.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static boolean reloaderThreadAlive() {
        return Thread.getAllStackTraces().keySet().stream()
                .anyMatch(thread -> RELOAD_THREAD.equals(thread.getName()) && thread.isAlive());
    }
}
