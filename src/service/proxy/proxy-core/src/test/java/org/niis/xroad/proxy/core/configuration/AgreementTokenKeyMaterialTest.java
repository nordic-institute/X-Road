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

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.agreementtoken.key.AgreementTokenKeyInfo;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.test.MutableClock;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgreementTokenKeyMaterialTest {

    @Mock
    private VaultClient vaultClient;

    @Mock
    private ProxyAgreementTokenProperties properties;

    @Mock
    private ProxyProperties proxyProperties;

    private final Map<String, String> keyPairsByKeyId = new HashMap<>();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));

    @BeforeEach
    void setUp() {
        lenient().when(vaultClient.getAgreementTokenSigningKeys()).thenAnswer(invocation -> new HashMap<>(keyPairsByKeyId));
        storeCreatedKeysInTheFakeSecretStore();
        lenient().when(properties.keyRefreshInterval()).thenReturn(Duration.ofSeconds(30));
        lenient().when(properties.maxKeyAge()).thenReturn(Duration.ofDays(30));
        lenient().when(proxyProperties.dspEnabled()).thenReturn(true);
    }

    private void storeCreatedKeysInTheFakeSecretStore() {
        lenient().doAnswer(invocation -> {
            keyPairsByKeyId.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(vaultClient).createAgreementTokenSigningKey(anyString(), anyString());
    }

    private AgreementTokenKeyMaterial newKeyMaterial() {
        return newKeyMaterial(Duration.ZERO);
    }

    private AgreementTokenKeyMaterial newKeyMaterial(Duration rotationDelay) {
        return new AgreementTokenKeyMaterial(vaultClient, properties, proxyProperties, clock, fixedDelay(rotationDelay));
    }

    private static RandomGenerator fixedDelay(Duration delay) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return delay.toMillis();
            }

            @Override
            public long nextLong(long bound) {
                return delay.toMillis();
            }
        };
    }

    @Test
    void providerStaysEmptyWhenTheVaultIsUnreachableAtConstruction() {
        when(vaultClient.getAgreementTokenSigningKeys()).thenThrow(new IllegalStateException("vault sealed"));

        var keyMaterial = newKeyMaterial();

        assertThat(keyMaterial.provider()).isEmpty();
    }

    @Test
    void providerIsPresentWhenTheVaultIsReachableAtConstruction() {
        var keyMaterial = newKeyMaterial();

        assertThat(keyMaterial.provider()).isPresent();
        assertThat(keyMaterial.provider().get().activeKey().keyId()).isEqualTo("1");
    }

    @Test
    void retryOrRefreshBuildsTheProviderOnceTheVaultBecomesReachable() {
        when(vaultClient.getAgreementTokenSigningKeys())
                .thenThrow(new IllegalStateException("vault sealed"))
                .thenAnswer(invocation -> new HashMap<>(keyPairsByKeyId));
        var keyMaterial = newKeyMaterial();
        assertThat(keyMaterial.provider()).isEmpty();

        keyMaterial.retryOrRefresh();

        assertThat(keyMaterial.provider()).isPresent();
    }

    @Test
    void retryOrRefreshCallsRefreshOnAnAlreadyBuiltProvider() {
        var keyMaterial = newKeyMaterial();
        assertThat(keyMaterial.provider()).isPresent();
        clearInvocations(vaultClient);

        keyMaterial.retryOrRefresh();

        verify(vaultClient, times(1)).getAgreementTokenSigningKeys();
    }

    @Test
    void refreshFailureLeavesTheLastGoodSnapshotInUse() {
        var keyMaterial = newKeyMaterial();
        var providerBeforeFailedRefresh = keyMaterial.provider().orElseThrow();
        var activeKeyBeforeFailedRefresh = providerBeforeFailedRefresh.activeKey();

        when(vaultClient.getAgreementTokenSigningKeys()).thenThrow(new IllegalStateException("vault sealed"));

        keyMaterial.retryOrRefresh();

        assertThat(keyMaterial.provider()).contains(providerBeforeFailedRefresh);
        assertThat(keyMaterial.provider().orElseThrow().activeKey()).isEqualTo(activeKeyBeforeFailedRefresh);
    }

    @Test
    void newestKeyYoungerThanTheMaximumAgeIsNotRotatedAndTheTickReadsTheStoreOnce() {
        var keyMaterial = newKeyMaterial();
        clock.advance(Duration.ofDays(29));
        clearInvocations(vaultClient);

        keyMaterial.retryOrRefresh();

        verify(vaultClient, times(1)).getAgreementTokenSigningKeys();
        assertThat(keyPairsByKeyId).containsOnlyKeys("1");
    }

    @Test
    void dueKeyIsRotatedOnlyOnceTheDrawnDelayHasPassed() {
        var keyMaterial = newKeyMaterial(Duration.ofSeconds(45));
        clock.advance(Duration.ofDays(31));

        keyMaterial.retryOrRefresh();
        assertThat(keyPairsByKeyId).containsOnlyKeys("1");

        clock.advance(Duration.ofSeconds(44));
        keyMaterial.retryOrRefresh();
        assertThat(keyPairsByKeyId).containsOnlyKeys("1");

        clock.advance(Duration.ofSeconds(1));
        keyMaterial.retryOrRefresh();
        assertThat(keyPairsByKeyId).containsOnlyKeys("1", "2");
    }

    @Test
    void newestKeyJustRotatedButNotYetSigningIsNotRotatedAgain() {
        var keyMaterial = newKeyMaterial();
        clock.advance(Duration.ofDays(31));
        keyMaterial.retryOrRefresh();
        assertThat(keyPairsByKeyId).containsOnlyKeys("1", "2");

        clock.advance(Duration.ofSeconds(15));
        keyMaterial.retryOrRefresh();

        var provider = keyMaterial.provider().orElseThrow();
        assertThat(provider.activeKey().keyId()).isEqualTo("1");
        assertThat(keyPairsByKeyId).containsOnlyKeys("1", "2");

        clock.advance(Duration.ofSeconds(15));
        keyMaterial.retryOrRefresh();

        assertThat(provider.activeKey().keyId()).isEqualTo("2");
        assertThat(keyPairsByKeyId).containsOnlyKeys("1", "2");
    }

    @Test
    void replicaSkipsItsRotationWhenAnotherReplicaRotatedDuringTheDelay() {
        var replicaA = newKeyMaterial(Duration.ofSeconds(10));
        var replicaB = newKeyMaterial(Duration.ofSeconds(40));
        clock.advance(Duration.ofDays(31));
        replicaA.retryOrRefresh();
        replicaB.retryOrRefresh();
        assertThat(keyPairsByKeyId).containsOnlyKeys("1");

        clock.advance(Duration.ofSeconds(10));
        replicaA.retryOrRefresh();
        assertThat(keyPairsByKeyId).containsOnlyKeys("1", "2");

        clock.advance(Duration.ofSeconds(30));
        replicaB.retryOrRefresh();

        assertThat(keyPairsByKeyId).containsOnlyKeys("1", "2");
    }

    @Test
    void newestKeyWithoutAnIssueTimeIsRotatedOnceAndThenNotAgain() throws Exception {
        keyPairsByKeyId.put("1", new ECKeyGenerator(Curve.P_256).keyID("1").generate().toJSONString());
        var keyMaterial = newKeyMaterial(Duration.ofSeconds(20));
        assertThat(keyMaterial.provider().orElseThrow().listKeys())
                .containsExactly(new AgreementTokenKeyInfo("1", Optional.empty(), true));

        keyMaterial.retryOrRefresh();
        assertThat(keyPairsByKeyId).containsOnlyKeys("1");

        clock.advance(Duration.ofSeconds(20));
        keyMaterial.retryOrRefresh();
        assertThat(keyPairsByKeyId).containsOnlyKeys("1", "2");

        clock.advance(Duration.ofSeconds(30));
        keyMaterial.retryOrRefresh();
        clock.advance(Duration.ofDays(1));
        keyMaterial.retryOrRefresh();
        assertThat(keyPairsByKeyId).containsOnlyKeys("1", "2");
    }

    @Test
    void noAutomaticRotationWhileDspIsDisabled() {
        var keyMaterial = newKeyMaterial();
        when(proxyProperties.dspEnabled()).thenReturn(false);
        clock.advance(Duration.ofDays(365));

        keyMaterial.retryOrRefresh();
        keyMaterial.retryOrRefresh();

        assertThat(keyPairsByKeyId).containsOnlyKeys("1");
    }

    @Test
    void noAutomaticRotationWhenTheMaximumKeyAgeIsZero() {
        var keyMaterial = newKeyMaterial();
        when(properties.maxKeyAge()).thenReturn(Duration.ZERO);
        clock.advance(Duration.ofDays(365));

        keyMaterial.retryOrRefresh();
        keyMaterial.retryOrRefresh();

        assertThat(keyPairsByKeyId).containsOnlyKeys("1");
    }

    @Test
    void failedRotationKeepsTheLastGoodSnapshotAndIsRetriedOnTheNextTick() {
        var keyMaterial = newKeyMaterial();
        var provider = keyMaterial.provider().orElseThrow();
        var keysBeforeFailure = provider.listKeys();
        clock.advance(Duration.ofDays(31));
        doThrow(new IllegalStateException("vault sealed"))
                .when(vaultClient).createAgreementTokenSigningKey(anyString(), anyString());

        keyMaterial.retryOrRefresh();

        assertThat(keyMaterial.provider()).contains(provider);
        assertThat(provider.listKeys()).isEqualTo(keysBeforeFailure);
        assertThat(keyPairsByKeyId).containsOnlyKeys("1");

        storeCreatedKeysInTheFakeSecretStore();
        clock.advance(Duration.ofSeconds(30));
        keyMaterial.retryOrRefresh();

        assertThat(keyPairsByKeyId).containsOnlyKeys("1", "2");
    }
}
