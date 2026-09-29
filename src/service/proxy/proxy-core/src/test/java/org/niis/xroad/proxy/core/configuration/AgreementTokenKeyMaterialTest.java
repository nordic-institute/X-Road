/*
 * The MIT License
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

package org.niis.xroad.proxy.core.configuration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.vault.VaultClient;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgreementTokenKeyMaterialTest {

    @Mock
    private VaultClient vaultClient;

    private final Map<String, String> keyPairsByKeyId = new HashMap<>();

    @BeforeEach
    void setUp() {
        lenient().when(vaultClient.getAgreementTokenSigningKeys()).thenAnswer(invocation -> new HashMap<>(keyPairsByKeyId));
        lenient().doAnswer(invocation -> {
            keyPairsByKeyId.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(vaultClient).createAgreementTokenSigningKey(anyString(), anyString());
    }

    @Test
    void providerStaysEmptyWhenTheVaultIsUnreachableAtConstruction() {
        when(vaultClient.getAgreementTokenSigningKeys()).thenThrow(new IllegalStateException("vault sealed"));

        var keyMaterial = new AgreementTokenKeyMaterial(vaultClient);

        assertThat(keyMaterial.provider()).isEmpty();
    }

    @Test
    void providerIsPresentWhenTheVaultIsReachableAtConstruction() {
        var keyMaterial = new AgreementTokenKeyMaterial(vaultClient);

        assertThat(keyMaterial.provider()).isPresent();
        assertThat(keyMaterial.provider().get().activeKey().keyId()).isEqualTo("1");
    }

    @Test
    void retryOrRefreshBuildsTheProviderOnceTheVaultBecomesReachable() {
        when(vaultClient.getAgreementTokenSigningKeys())
                .thenThrow(new IllegalStateException("vault sealed"))
                .thenAnswer(invocation -> new HashMap<>(keyPairsByKeyId));
        var keyMaterial = new AgreementTokenKeyMaterial(vaultClient);
        assertThat(keyMaterial.provider()).isEmpty();

        keyMaterial.retryOrRefresh();

        assertThat(keyMaterial.provider()).isPresent();
    }

    @Test
    void retryOrRefreshCallsRefreshOnAnAlreadyBuiltProvider() {
        var keyMaterial = new AgreementTokenKeyMaterial(vaultClient);
        assertThat(keyMaterial.provider()).isPresent();
        clearInvocations(vaultClient);

        keyMaterial.retryOrRefresh();

        verify(vaultClient, times(1)).getAgreementTokenSigningKeys();
    }

    @Test
    void refreshFailureLeavesTheLastGoodSnapshotInUse() {
        var keyMaterial = new AgreementTokenKeyMaterial(vaultClient);
        var providerBeforeFailedRefresh = keyMaterial.provider().orElseThrow();
        var activeKeyBeforeFailedRefresh = providerBeforeFailedRefresh.activeKey();

        when(vaultClient.getAgreementTokenSigningKeys()).thenThrow(new IllegalStateException("vault sealed"));

        keyMaterial.retryOrRefresh();

        assertThat(keyMaterial.provider()).contains(providerBeforeFailedRefresh);
        assertThat(keyMaterial.provider().orElseThrow().activeKey()).isEqualTo(activeKeyBeforeFailedRefresh);
    }
}
