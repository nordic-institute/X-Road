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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.rpc.RpcProperties;
import org.niis.xroad.common.rpc.credentials.ChannelTrust;
import org.niis.xroad.common.vault.VaultClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.cert.X509Certificate;
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
    private final OpMonitorChannelTrustResolver resolver =
            new OpMonitorChannelTrustResolver(rpcProperties, vaultClient);

    @BeforeEach
    void tlsOn() {
        when(rpcProperties.useTls()).thenReturn(true);
    }

    @Test
    void noImportedCaGivesTheSharedTrust() {
        when(vaultClient.getOpMonitorCa()).thenReturn(Optional.empty());

        assertThat(resolver.resolve()).isEqualTo(ChannelTrust.shared());
    }

    @Test
    void importedCaGivesTrustInThatCaAlone() {
        var ca = mock(X509Certificate.class);
        when(vaultClient.getOpMonitorCa()).thenReturn(Optional.of(ca));

        assertThat(resolver.resolve())
                .isInstanceOfSatisfying(ChannelTrust.OnlyCa.class, trust -> assertThat(trust.ca()).isSameAs(ca));
    }

    @ParameterizedTest
    @MethodSource("unreadableCaCases")
    void unreadableImportedCaFailsInsteadOfFallingBackToTheSharedTrust(String situation, Throwable readFailure) {
        when(vaultClient.getOpMonitorCa()).thenThrow(readFailure);

        assertThatThrownBy(resolver::resolve)
                .as(situation)
                .isInstanceOfSatisfying(XrdRuntimeException.class, e -> {
                    assertTrustFailure(e);
                    assertThat(e.getCause()).isSameAs(readFailure);
                });
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
    void malformedCaCertificateFailsInsteadOfFallingBackToTheSharedTrust() {
        var realReader = mock(VaultClient.class, CALLS_REAL_METHODS);
        doAnswer(_ -> realReader.toOpMonitorCa(Map.of(VaultClient.CERTIFICATE_KEY, "not a certificate")))
                .when(realReader).getOpMonitorCa();
        var resolverWithRealReader = new OpMonitorChannelTrustResolver(rpcProperties, realReader);

        assertThatThrownBy(resolverWithRealReader::resolve)
                .isInstanceOfSatisfying(XrdRuntimeException.class, e -> {
                    assertTrustFailure(e);
                    assertThat(e.getCause()).isInstanceOf(XrdRuntimeException.class)
                            .hasMessageContaining("Could not parse certificate");
                });
    }

    private static void assertTrustFailure(XrdRuntimeException e) {
        assertThat(e.getCode()).isEqualTo(ErrorCode.OP_MONITOR_TRUST_FAILURE.code());
        assertThat(e.getDetails()).isEqualTo("The op-monitor channel trust could not be established");
        assertThat(e.getErrorCodeMetadata()).isEmpty();
        assertThat(e.getMessage()).doesNotContain("status 403", "sealed", "connection refused", "Could not parse");
    }

    @Test
    void tlsDisabledGivesTheSharedTrustWithoutReadingTheCa() {
        when(rpcProperties.useTls()).thenReturn(false);

        assertThat(resolver.resolve()).isEqualTo(ChannelTrust.shared());
        verifyNoInteractions(vaultClient);
    }

    @Test
    void tlsDisabledIgnoresAStoredCa() {
        when(rpcProperties.useTls()).thenReturn(false);
        when(vaultClient.getOpMonitorCa()).thenReturn(Optional.of(mock(X509Certificate.class)));

        assertThat(resolver.resolve()).isEqualTo(ChannelTrust.shared());
        verify(vaultClient, never()).getOpMonitorCa();
    }

    @Test
    void tlsDisabledIgnoresAFailingCaRead() {
        when(rpcProperties.useTls()).thenReturn(false);
        when(vaultClient.getOpMonitorCa()).thenThrow(new IllegalStateException("sealed"));

        assertThat(resolver.resolve()).isEqualTo(ChannelTrust.shared());
        verify(vaultClient, never()).getOpMonitorCa();
    }
}
