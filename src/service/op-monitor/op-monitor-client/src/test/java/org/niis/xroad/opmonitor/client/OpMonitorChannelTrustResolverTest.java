/*
 * The MIT License
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
package org.niis.xroad.opmonitor.client;

import io.grpc.ChannelCredentials;
import io.grpc.TlsChannelCredentials;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.rpc.RpcProperties;
import org.niis.xroad.common.rpc.VaultKeyProvider;
import org.niis.xroad.common.rpc.credentials.RpcCredentialsConfigurer;
import org.niis.xroad.common.rpc.test.TestCa;
import org.niis.xroad.common.rpc.test.TestVaultKeyProvider;
import org.niis.xroad.common.vault.VaultClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OpMonitorChannelTrustResolverTest {

    private final RpcProperties rpcProperties = mock(RpcProperties.class);
    private final VaultClient vaultClient = mock(VaultClient.class);
    private final VaultKeyProvider vaultKeyProvider = mock(VaultKeyProvider.class);
    private final RpcCredentialsConfigurer sharedCredentials = mock(RpcCredentialsConfigurer.class);
    private final OpMonitorChannelTrustResolver resolver =
            new OpMonitorChannelTrustResolver(rpcProperties, vaultClient, vaultKeyProvider, sharedCredentials);

    @BeforeEach
    void tlsOn() {
        when(rpcProperties.useTls()).thenReturn(true);
    }

    @Test
    void noImportedCaGivesTheSharedCredentialsUnchanged() {
        var shared = mock(ChannelCredentials.class);
        when(vaultClient.getOpMonitorCa()).thenReturn(Optional.empty());
        when(sharedCredentials.createClientCredentials()).thenReturn(shared);

        assertThat(resolver.resolve()).isSameAs(shared);
        verifyNoInteractions(vaultKeyProvider);
    }

    @Test
    void importedCaGivesDedicatedCredentialsAndNotTheSharedOnes() throws Exception {
        var ca = TestCa.selfSigned("Op-monitor CA");
        var identity = ca.issueLeaf("proxy", List.of("localhost"), List.of());
        var resolverWithKeys = new OpMonitorChannelTrustResolver(rpcProperties, vaultClient,
                new TestVaultKeyProvider(identity, ca.certificate()), sharedCredentials);
        when(vaultClient.getOpMonitorCa()).thenReturn(Optional.of(ca.certificate()));

        assertThat(resolverWithKeys.resolve()).isInstanceOf(TlsChannelCredentials.class);
        verifyNoInteractions(sharedCredentials);
    }

    @Test
    void blankOrMissingRecordGivesTheSharedCredentialsUnchanged() {
        var shared = mock(ChannelCredentials.class);
        when(vaultClient.getOpMonitorCa()).thenReturn(Optional.empty());
        when(sharedCredentials.createClientCredentials()).thenReturn(shared);

        assertThat(resolver.resolve()).isSameAs(shared);
        assertThat(resolver.resolve()).isSameAs(shared);
        verifyNoInteractions(vaultKeyProvider);
    }

    @ParameterizedTest
    @MethodSource("unreadableCaCases")
    void unreadableImportedCaFailsInsteadOfFallingBackToTheSharedCredentials(String situation, Throwable readFailure) {
        when(vaultClient.getOpMonitorCa()).thenThrow(readFailure);

        assertThatThrownBy(resolver::resolve)
                .as(situation)
                .isInstanceOfSatisfying(XrdRuntimeException.class, e -> {
                    assertTrustFailure(e);
                    assertThat(e.getCause()).isSameAs(readFailure);
                });
        verifyNoInteractions(sharedCredentials, vaultKeyProvider);
    }

    static Stream<Arguments> unreadableCaCases() {
        return Stream.of(
                Arguments.of("OpenBao answers 403", new IllegalArgumentException("status 403")),
                Arguments.of("OpenBao answers 500", new IllegalArgumentException("status 500")),
                Arguments.of("OpenBao is sealed", new IllegalStateException("vault is sealed")),
                Arguments.of("OpenBao is not reachable", new UncheckedIOException(new IOException("connection refused"))),
                Arguments.of("KV engine is not initialised",
                        new IllegalStateException("Vault KV Secret Engine is not initialized. Check configuration.")));
    }

    @Test
    void malformedCaCertificateFailsInsteadOfFallingBackToTheSharedCredentials() {
        var realReader = mock(VaultClient.class, CALLS_REAL_METHODS);
        doAnswer(_ -> realReader.toOpMonitorCa(Map.of(VaultClient.CERTIFICATE_KEY, "not a certificate")))
                .when(realReader).getOpMonitorCa();
        var resolverWithRealReader = new OpMonitorChannelTrustResolver(rpcProperties, realReader, vaultKeyProvider,
                sharedCredentials);

        assertThatThrownBy(resolverWithRealReader::resolve)
                .isInstanceOfSatisfying(XrdRuntimeException.class, e -> {
                    assertTrustFailure(e);
                    assertThat(e.getCause()).isInstanceOf(XrdRuntimeException.class)
                            .hasMessageContaining("Could not parse certificate");
                });
        verifyNoInteractions(sharedCredentials, vaultKeyProvider);
    }

    @Test
    void unusableKeyManagerFailsInsteadOfFallingBackToTheSharedCredentials() {
        var keyFailure = new IllegalStateException("key manager is not ready");
        when(vaultClient.getOpMonitorCa()).thenReturn(Optional.of(mock(X509Certificate.class)));
        when(vaultKeyProvider.getKeyManager()).thenThrow(keyFailure);

        assertThatThrownBy(resolver::resolve)
                .isInstanceOfSatisfying(XrdRuntimeException.class, e -> {
                    assertTrustFailure(e);
                    assertThat(e.getCause()).isSameAs(keyFailure);
                });
        verifyNoInteractions(sharedCredentials);
    }

    private static void assertTrustFailure(XrdRuntimeException e) {
        assertThat(e.getCode()).isEqualTo(ErrorCode.OP_MONITOR_TRUST_FAILURE.code());
        assertThat(e.getDetails()).isEqualTo("The op-monitor channel trust could not be established");
        assertThat(e.getErrorCodeMetadata()).isEmpty();
        assertThat(e.getMessage()).doesNotContain("status 403", "sealed", "connection refused", "Could not parse",
                "key manager is not ready");
    }

    @Test
    void tlsDisabledUsesTheSharedCredentialsWithoutReadingTheCa() {
        var shared = mock(ChannelCredentials.class);
        when(rpcProperties.useTls()).thenReturn(false);
        when(sharedCredentials.createClientCredentials()).thenReturn(shared);

        assertThat(resolver.resolve()).isSameAs(shared);
        verifyNoInteractions(vaultClient, vaultKeyProvider);
    }

    @Test
    void tlsDisabledIgnoresAStoredCa() {
        var shared = mock(ChannelCredentials.class);
        when(rpcProperties.useTls()).thenReturn(false);
        when(vaultClient.getOpMonitorCa()).thenReturn(Optional.of(mock(X509Certificate.class)));
        when(sharedCredentials.createClientCredentials()).thenReturn(shared);

        assertThat(resolver.resolve()).isSameAs(shared);
        verify(vaultClient, never()).getOpMonitorCa();
        verifyNoInteractions(vaultKeyProvider);
    }

    @Test
    void tlsDisabledIgnoresAFailingCaRead() {
        var shared = mock(ChannelCredentials.class);
        when(rpcProperties.useTls()).thenReturn(false);
        when(vaultClient.getOpMonitorCa()).thenThrow(new IllegalStateException("sealed"));
        when(sharedCredentials.createClientCredentials()).thenReturn(shared);

        assertThat(resolver.resolve()).isSameAs(shared);
        verify(vaultClient, never()).getOpMonitorCa();
        verifyNoInteractions(vaultKeyProvider);
    }
}
