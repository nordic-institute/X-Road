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
package org.niis.xroad.common.agreementtoken.key;

import com.nimbusds.jose.jwk.Curve;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.vault.VaultClient;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VaultAgreementTokenKeyProviderTest {

    @Mock
    private VaultClient vaultClient;

    private final Map<String, String> keyPairsByKeyId = new HashMap<>();

    @BeforeEach
    void setUp() {
        lenient().when(vaultClient.getAgreementTokenSigningKeys()).thenAnswer(invocation -> new HashMap<>(keyPairsByKeyId));
        lenient().doAnswer(invocation -> {
            String keyId = invocation.getArgument(0);
            String keyPairJwk = invocation.getArgument(1);
            keyPairsByKeyId.put(keyId, keyPairJwk);
            return null;
        }).when(vaultClient).createAgreementTokenSigningKey(anyString(), anyString());
    }

    @Test
    void shouldBootstrapAKeyWhenVaultHasNone() {
        var provider = new VaultAgreementTokenKeyProvider(vaultClient, new SecureRandom());

        assertThat(provider.activeKey().keyId()).isEqualTo("1");
        assertThat(provider.activeKey().keyPair().getCurve()).isEqualTo(Curve.P_256);
        assertThat(provider.activeKey().keyPair().isPrivate()).isTrue();
        assertThat(keyPairsByKeyId).containsKey("1");
        verify(vaultClient).createAgreementTokenSigningKey(anyString(), anyString());
    }

    @Test
    void shouldNotBootstrapWhenVaultAlreadyHasAKey() {
        putStoredKey("1");

        new VaultAgreementTokenKeyProvider(vaultClient, new SecureRandom());

        verify(vaultClient, never()).createAgreementTokenSigningKey(anyString(), anyString());
    }

    @Test
    void shouldTreatTheHighestVersionedKeyIdAsActive() {
        putStoredKey("1");
        putStoredKey("2");
        putStoredKey("10");

        var provider = new VaultAgreementTokenKeyProvider(vaultClient, new SecureRandom());

        assertThat(provider.activeKey().keyId()).isEqualTo("10");
    }

    @Test
    void shouldKeepPreviousKeysReadableAfterRotation() {
        putStoredKey("1");
        var provider = new VaultAgreementTokenKeyProvider(vaultClient, new SecureRandom());
        var previousKeyId = provider.activeKey().keyId();

        var rotated = provider.rotate();

        assertThat(rotated.keyId()).isNotEqualTo(previousKeyId);
        assertThat(provider.activeKey().keyId()).isEqualTo(rotated.keyId());
        assertThat(provider.keyById(previousKeyId)).isPresent();
        assertThat(provider.keyById(rotated.keyId())).isPresent();
    }

    @Test
    void shouldFailRatherThanCacheAStoredValueThatIsNotAP256KeyPair() {
        keyPairsByKeyId.put("1", "not-a-jwk");
        assertThatThrownBy(() -> new VaultAgreementTokenKeyProvider(vaultClient, new SecureRandom()))
                .isInstanceOf(XrdRuntimeException.class);

        keyPairsByKeyId.put("1", TestKeyPairs.generate().toPublicJWK().toJSONString());
        assertThatThrownBy(() -> new VaultAgreementTokenKeyProvider(vaultClient, new SecureRandom()))
                .isInstanceOf(XrdRuntimeException.class);

        verify(vaultClient, never()).createAgreementTokenSigningKey(anyString(), anyString());
    }

    @Test
    void shouldReturnEmptyForUnknownKeyId() {
        putStoredKey("1");
        var provider = new VaultAgreementTokenKeyProvider(vaultClient, new SecureRandom());

        assertThat(provider.keyById("does-not-exist")).isEmpty();
    }

    @Test
    void shouldFailFastFromConstructorAndNeverBootstrapWhenInitialListingFails() {
        when(vaultClient.getAgreementTokenSigningKeys()).thenThrow(new IllegalStateException("vault sealed"));

        assertThatThrownBy(() -> new VaultAgreementTokenKeyProvider(vaultClient, new SecureRandom()))
                .isInstanceOf(XrdRuntimeException.class);

        verify(vaultClient, never()).createAgreementTokenSigningKey(anyString(), anyString());
    }

    @Test
    void shouldRaiseAgreementTokenExceptionWhenVaultWriteFails() {
        lenient().when(vaultClient.getAgreementTokenSigningKeys()).thenReturn(Map.of());
        doThrow(new IllegalStateException("vault unreachable"))
                .when(vaultClient).createAgreementTokenSigningKey(any(), any());

        assertThatThrownBy(() -> new VaultAgreementTokenKeyProvider(vaultClient, new SecureRandom()))
                .isInstanceOf(XrdRuntimeException.class);
    }

    @Test
    void shouldKeepThePreviousSnapshotAndRaiseWhenARefreshFails() {
        putStoredKey("1");
        var provider = new VaultAgreementTokenKeyProvider(vaultClient, new SecureRandom());
        var goodKey = provider.activeKey();

        when(vaultClient.getAgreementTokenSigningKeys()).thenThrow(new IllegalStateException("vault sealed"));

        assertThatThrownBy(provider::refresh).isInstanceOf(XrdRuntimeException.class);
        assertThat(provider.activeKey()).isEqualTo(goodKey);
        assertThat(provider.keyById("1")).isPresent();
    }

    @Test
    void shouldNotTreatAFailedListingAsEmptyDuringBootstrap() {
        // Vault has genuinely never had a key, but the very first listing call transiently fails: the
        // constructor must fail fast rather than read that failure as "no keys yet" and bootstrap one.
        when(vaultClient.getAgreementTokenSigningKeys())
                .thenThrow(new IllegalStateException("vault sealed"))
                .thenAnswer(invocation -> new HashMap<>(keyPairsByKeyId));

        assertThatThrownBy(() -> new VaultAgreementTokenKeyProvider(vaultClient, new SecureRandom()))
                .isInstanceOf(XrdRuntimeException.class);

        verify(vaultClient, never()).createAgreementTokenSigningKey(anyString(), anyString());
    }

    @Test
    void shouldReListVaultBeforeRotatingAndSkipAnIdThatAppearedMeanwhile() {
        putStoredKey("1");
        var provider = new VaultAgreementTokenKeyProvider(vaultClient, new SecureRandom());

        // Another replica creates key "2" after our constructor's refresh() but before rotate() runs; the
        // provider's cached snapshot still only knows about "1".
        putStoredKey("2");

        var rotated = provider.rotate();

        assertThat(rotated.keyId()).isEqualTo("3");
        verify(vaultClient, never()).createAgreementTokenSigningKey(eq("2"), anyString());
        assertThat(provider.keyById("1")).isPresent();
        assertThat(provider.keyById("2")).isPresent();
        assertThat(provider.keyById("3")).isPresent();
    }

    @Test
    void shouldAdoptAKeyThatAppearedBetweenTheInitialRefreshAndBootstrap() {
        // Simulates two replicas starting against an empty vault at once: this provider's constructor sees
        // an empty listing, but by the time bootstrap() re-lists right before writing, another replica has
        // already created key "1" — bootstrap must adopt it rather than overwrite it.
        when(vaultClient.getAgreementTokenSigningKeys())
                .thenReturn(Map.of())
                .thenAnswer(invocation -> {
                    putStoredKey("1");
                    return new HashMap<>(keyPairsByKeyId);
                })
                .thenAnswer(invocation -> new HashMap<>(keyPairsByKeyId));

        var provider = new VaultAgreementTokenKeyProvider(vaultClient, new SecureRandom());

        assertThat(provider.activeKey().keyId()).isEqualTo("1");
        verify(vaultClient, never()).createAgreementTokenSigningKey(anyString(), anyString());
    }

    private void putStoredKey(String keyId) {
        keyPairsByKeyId.put(keyId, TestKeyPairs.generate().toJSONString());
    }
}
