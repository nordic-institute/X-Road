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
package org.niis.xroad.common.rpc.credentials;

import io.grpc.InsecureChannelCredentials;
import io.grpc.InsecureServerCredentials;
import io.grpc.util.AdvancedTlsX509KeyManager;
import io.grpc.util.AdvancedTlsX509TrustManager;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.rpc.VaultKeyProvider;
import org.niis.xroad.common.rpc.test.TestCa;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RpcCredentialsConfigurerTest {

    @Test
    void tlsOnlyCaWithAnUnusableKeyManagerFailsWithXrdRuntimeException() throws Exception {
        var keyFailure = new IllegalStateException("key manager is not ready");
        var keyProvider = mock(VaultKeyProvider.class);
        when(keyProvider.getKeyManager()).thenThrow(keyFailure);
        var ca = TestCa.selfSigned("Test CA").certificate();

        assertThatThrownBy(() -> new TlsRpcCredentialsConfigurer(keyProvider).createClientCredentials(ChannelTrust.onlyCa(ca)))
                .isInstanceOfSatisfying(XrdRuntimeException.class, e -> assertThat(e.getCause()).isSameAs(keyFailure));
    }

    @Test
    void tlsClientNamesWithAProviderThatCannotVerifyFailWithXrdRuntimeException() {
        var keyProvider = mock(VaultKeyProvider.class);
        when(keyProvider.getKeyManager()).thenReturn(new AdvancedTlsX509KeyManager());
        when(keyProvider.createVerifyingTrustManager(any())).thenCallRealMethod();

        assertThatThrownBy(() -> new TlsRpcCredentialsConfigurer(keyProvider).createServerCredentials(List.of("proxy")))
                .isInstanceOfSatisfying(XrdRuntimeException.class,
                        e -> assertThat(e.getCause()).isInstanceOf(UnsupportedOperationException.class));
    }

    @Test
    void tlsEmptyClientNamesUseTheSharedTrustManagerAndNeverAskForAVerifyingOne() throws Exception {
        var keyProvider = mock(VaultKeyProvider.class);
        when(keyProvider.getKeyManager()).thenReturn(new AdvancedTlsX509KeyManager());
        when(keyProvider.getTrustManager()).thenReturn(AdvancedTlsX509TrustManager.newBuilder().build());

        assertThat(new TlsRpcCredentialsConfigurer(keyProvider).createServerCredentials(List.of())).isNotNull();

        verify(keyProvider).getTrustManager();
        verify(keyProvider, never()).createVerifyingTrustManager(any());
    }

    @Test
    void insecureCredentialsIgnoreTrustAndClientNames() throws Exception {
        var configurer = new InsecureRpcCredentialsConfigurer();
        var ca = TestCa.selfSigned("Test CA").certificate();

        assertThat(configurer.createClientCredentials(ChannelTrust.onlyCa(ca)))
                .isInstanceOf(InsecureChannelCredentials.create().getClass());
        assertThat(configurer.createClientCredentials(ChannelTrust.shared()))
                .isInstanceOf(InsecureChannelCredentials.create().getClass());
        assertThat(configurer.createServerCredentials(List.of("proxy")))
                .isInstanceOf(InsecureServerCredentials.create().getClass());
    }
}
