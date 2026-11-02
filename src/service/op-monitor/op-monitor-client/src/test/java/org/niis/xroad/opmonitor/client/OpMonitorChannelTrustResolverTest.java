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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.rpc.RpcProperties;
import org.niis.xroad.common.rpc.VaultKeyProvider;
import org.niis.xroad.common.rpc.credentials.RpcCredentialsConfigurer;
import org.niis.xroad.common.vault.VaultClient;

import java.security.cert.X509Certificate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    void unreadableImportedCaFailsInsteadOfFallingBackToTheSharedCredentials() {
        when(vaultClient.getOpMonitorCa()).thenThrow(new IllegalStateException("sealed"));

        assertThatThrownBy(resolver::resolve).isInstanceOf(XrdRuntimeException.class);
        verifyNoInteractions(sharedCredentials);
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
