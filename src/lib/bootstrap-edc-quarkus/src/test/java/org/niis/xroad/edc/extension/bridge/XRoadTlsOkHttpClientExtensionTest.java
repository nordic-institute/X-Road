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

import okhttp3.Request;
import org.eclipse.edc.spi.monitor.ConsoleMonitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.edc.extension.bridge.trust.TlsTestSupport;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.ApprovedDsTlsCaInfo;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end coverage of the production wiring: settings and the {@code QUARKUS_VAULT_TLS_CA_CERT}
 * env var (passed directly rather than read from the real environment, for testability) feeding a
 * real {@link okhttp3.OkHttpClient} that is exercised with real HTTP requests over real handshakes.
 */
@ExtendWith(MockitoExtension.class)
class XRoadTlsOkHttpClientExtensionTest {

    private static final String INSTANCE_IDENTIFIER = "TEST-INSTANCE";

    @Mock
    private GlobalConfProvider globalConfProvider;

    private ServiceExtensionContext context;
    private XRoadTlsOkHttpClientExtension extension;

    private void setUp(String vaultUrlSetting) throws Exception {
        extension = new XRoadTlsOkHttpClientExtension();
        setField(extension, "globalConfProvider", globalConfProvider);
        setField(extension, "reloadIntervalSeconds", 3600L);

        context = mock(ServiceExtensionContext.class);
        lenient().when(context.getMonitor()).thenReturn(new ConsoleMonitor());
        lenient().when(context.getSetting("edc.vault.hashicorp.url", null)).thenReturn(vaultUrlSetting);

        lenient().when(globalConfProvider.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);
    }

    @Test
    void listedCaAcceptsAndUnlistedCaRejectsOverARealOkHttpClient() throws Exception {
        setUp(null);
        var ca = TlsTestSupport.generateCa("DS TLS CA");
        var leaf = TlsTestSupport.issueLocalhostLeaf(ca);
        var otherCa = TlsTestSupport.generateCa("Unlisted CA");
        var otherLeaf = TlsTestSupport.issueLocalhostLeaf(otherCa);
        when(globalConfProvider.getApprovedDsTlsCas(INSTANCE_IDENTIFIER)).thenReturn(List.of(approvedCa(ca)));

        var client = extension.buildOkHttpClient(context, null);
        try (var listedServer = TlsTestSupport.TestHttpsServer.start(TlsTestSupport.serverKeyStore(ca, leaf));
                var unlistedServer = TlsTestSupport.TestHttpsServer.start(TlsTestSupport.serverKeyStore(otherCa, otherLeaf))) {
            try (var response = client.newCall(get(listedServer.port())).execute()) {
                assertThat(response.isSuccessful()).isTrue();
            }
            assertThatThrownBy(() -> client.newCall(get(unlistedServer.port())).execute()).isInstanceOf(IOException.class);
        } finally {
            extension.shutdown();
        }
    }

    @Test
    void vaultCaSetWithAnEmptyListAcceptsTheVaultHostAndRejectsAnotherHostWithTheSameCa() throws Exception {
        var vaultCa = TlsTestSupport.generateCa("OpenBao Test CA");
        var vaultLeaf = TlsTestSupport.issueLocalhostLeaf(vaultCa);
        var otherLeaf = TlsTestSupport.issueLocalhostLeaf(vaultCa);

        try (var vaultServer = TlsTestSupport.TestHttpsServer.start(TlsTestSupport.serverKeyStore(vaultCa, vaultLeaf));
                var otherServer = TlsTestSupport.TestHttpsServer.start(TlsTestSupport.serverKeyStore(vaultCa, otherLeaf))) {
            setUp("https://localhost:" + vaultServer.port());
            when(globalConfProvider.getApprovedDsTlsCas(INSTANCE_IDENTIFIER)).thenReturn(List.of());

            var vaultCaCertFile = Files.createTempFile("vault-ca", ".der");
            Files.write(vaultCaCertFile, vaultCa.certificate().getEncoded());

            var client = extension.buildOkHttpClient(context, vaultCaCertFile.toString());
            try {
                try (var response = client.newCall(get(vaultServer.port())).execute()) {
                    assertThat(response.isSuccessful()).isTrue();
                }
                assertThatThrownBy(() -> client.newCall(get(otherServer.port())).execute()).isInstanceOf(IOException.class);
            } finally {
                extension.shutdown();
                Files.deleteIfExists(vaultCaCertFile);
            }
        }
    }

    @Test
    void withTheVaultCaCertEnvUnsetTheVaultHostGetsPureListOnlyTrust() throws Exception {
        var vaultCa = TlsTestSupport.generateCa("OpenBao Test CA");
        var vaultLeaf = TlsTestSupport.issueLocalhostLeaf(vaultCa);

        try (var vaultServer = TlsTestSupport.TestHttpsServer.start(TlsTestSupport.serverKeyStore(vaultCa, vaultLeaf))) {
            setUp("https://localhost:" + vaultServer.port());
            when(globalConfProvider.getApprovedDsTlsCas(INSTANCE_IDENTIFIER)).thenReturn(List.of());

            // No vault CA cert path supplied, exactly as when QUARKUS_VAULT_TLS_CA_CERT is unset.
            var client = extension.buildOkHttpClient(context, null);
            try {
                assertThatThrownBy(() -> client.newCall(get(vaultServer.port())).execute()).isInstanceOf(IOException.class);
            } finally {
                extension.shutdown();
            }
        }
    }

    private static Request get(int port) {
        return new Request.Builder().url("https://localhost:" + port + "/").build();
    }

    private static ApprovedDsTlsCaInfo approvedCa(TlsTestSupport.TestCa ca) throws Exception {
        return new ApprovedDsTlsCaInfo(ca.certificate().getSubjectX500Principal().getName(),
                ca.certificate().getEncoded(), List.of(), null, null);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
