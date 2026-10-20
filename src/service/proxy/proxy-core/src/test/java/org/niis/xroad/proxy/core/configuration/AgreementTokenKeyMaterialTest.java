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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.agreementtoken.AgreementTokenGrant;
import org.niis.xroad.common.agreementtoken.AgreementTokenMinter;
import org.niis.xroad.common.agreementtoken.AgreementTokenProtocolProperties;
import org.niis.xroad.common.agreementtoken.AgreementTokenRejectionReason;
import org.niis.xroad.common.agreementtoken.AgreementTokenRequestContext;
import org.niis.xroad.common.agreementtoken.AgreementTokenScope;
import org.niis.xroad.common.agreementtoken.AgreementTokenVerificationResult;
import org.niis.xroad.common.agreementtoken.AgreementTokenVerifier;
import org.niis.xroad.common.agreementtoken.key.AgreementTokenKeyInfo;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.test.MutableClock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgreementTokenKeyMaterialTest {

    private static final Duration RETENTION = Duration.ofHours(1);

    private static final AgreementTokenProtocolProperties TOKEN_PROPERTIES = new AgreementTokenProtocolProperties() {
        @Override
        public String issuer() {
            return "xroad-agreement-token";
        }

        @Override
        public String audience() {
            return "security-server-proxy";
        }

        @Override
        public Duration tokenTtl() {
            return Duration.ofSeconds(60);
        }
    };

    private static final ClientId CONSUMER = ClientId.Conf.create("DEV", "COM", "222", "TESTCLIENT");
    private static final ServiceId SERVICE = ServiceId.Conf.create(ClientId.Conf.create("DEV", "COM", "333", "PROVIDER"),
            "getData", "v1");
    private static final AgreementTokenGrant GRANT =
            new AgreementTokenGrant("agreement-1", CONSUMER, SERVICE, List.of(new AgreementTokenScope("*", "**")));
    private static final AgreementTokenRequestContext CONTEXT = AgreementTokenRequestContext.forSoap(CONSUMER, SERVICE);

    @Mock
    private VaultClient vaultClient;

    @Mock
    private ProxyAgreementTokenProperties properties;

    @Mock
    private ProxyProperties proxyProperties;

    private final Map<String, String> keyPairsByKeyId = new HashMap<>();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final Logger materialLogger = Logger.getLogger(AgreementTokenKeyMaterial.class.getName());
    private final RecordingHandler logHandler = new RecordingHandler();
    private Level originalLogLevel;

    @BeforeEach
    void setUp() {
        lenient().when(vaultClient.getAgreementTokenSigningKeys()).thenAnswer(invocation -> new HashMap<>(keyPairsByKeyId));
        storeCreatedKeysInTheFakeSecretStore();
        removeDeletedKeysFromTheFakeSecretStore();
        lenient().when(properties.keyRefreshInterval()).thenReturn(Duration.ofSeconds(30));
        lenient().when(properties.maxKeyAge()).thenReturn(Duration.ofDays(30));
        lenient().when(properties.keyRetention()).thenReturn(Duration.ZERO);
        lenient().when(proxyProperties.dspEnabled()).thenReturn(true);
        originalLogLevel = materialLogger.getLevel();
        materialLogger.addHandler(logHandler);
        materialLogger.setLevel(Level.ALL);
    }

    @AfterEach
    void tearDown() {
        materialLogger.removeHandler(logHandler);
        materialLogger.setLevel(originalLogLevel);
    }

    private void removeDeletedKeysFromTheFakeSecretStore() {
        lenient().doAnswer(invocation -> {
            keyPairsByKeyId.remove(invocation.<String>getArgument(0));
            return null;
        }).when(vaultClient).deleteAgreementTokenSigningKey(anyString());
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

    @Test
    void aKeyIsDeletedOnceTheRetentionHasPassedSinceItStoppedSigning() {
        when(properties.keyRetention()).thenReturn(RETENTION);
        var keyMaterial = newKeyMaterial();
        storeThreeKeysTheFirstTenDaysOld(keyMaterial);
        advanceUntilOnlyTheFirstKeyIsDue();

        keyMaterial.retryOrRefresh();

        verify(vaultClient).deleteAgreementTokenSigningKey("1");
        verify(vaultClient, times(1)).deleteAgreementTokenSigningKey(anyString());
        assertThat(keyPairsByKeyId).containsOnlyKeys("2", "3");
        assertThat(keyMaterial.provider().orElseThrow().activeKey().keyId()).isEqualTo("3");
    }

    @Test
    void theSnapshotDropsADeletedKeyOnTheNextRefresh() {
        when(properties.keyRetention()).thenReturn(RETENTION);
        var keyMaterial = newKeyMaterial();
        storeThreeKeysTheFirstTenDaysOld(keyMaterial);
        advanceUntilOnlyTheFirstKeyIsDue();

        keyMaterial.retryOrRefresh();
        keyMaterial.retryOrRefresh();

        assertThat(keyMaterial.provider().orElseThrow().listKeys())
                .extracting(AgreementTokenKeyInfo::keyId).containsExactly("2", "3");
    }

    @Test
    void aKeyThatStoppedSigningLessThanTheRetentionAgoIsKeptHoweverOldItIs() {
        when(properties.keyRetention()).thenReturn(RETENTION);
        var keyMaterial = newKeyMaterial();
        storeThreeKeysTheFirstTenDaysOld(keyMaterial);

        keyMaterial.retryOrRefresh();

        verify(vaultClient, never()).deleteAgreementTokenSigningKey(anyString());
        assertThat(keyPairsByKeyId).containsOnlyKeys("1", "2", "3");
    }

    @Test
    void theSigningKeyIsKeptWhileItsSuccessorHasNotStartedSigning() {
        when(properties.keyRetention()).thenReturn(RETENTION);
        var keyMaterial = newKeyMaterial();
        clock.advance(Duration.ofDays(10));
        keyMaterial.provider().orElseThrow().rotate();

        keyMaterial.retryOrRefresh();

        var provider = keyMaterial.provider().orElseThrow();
        assertThat(provider.activeKey().keyId()).isEqualTo("1");
        verify(vaultClient, never()).deleteAgreementTokenSigningKey(anyString());
        assertThat(keyPairsByKeyId).containsOnlyKeys("1", "2");
    }

    @Test
    void theHighestIdIsKeptHoweverLongAgoItWasIssued() {
        when(properties.keyRetention()).thenReturn(RETENTION);
        var keyMaterial = newKeyMaterial();
        clock.advance(Duration.ofDays(10));
        keyMaterial.provider().orElseThrow().rotate();
        clock.advance(Duration.ofDays(10));

        keyMaterial.retryOrRefresh();

        assertThat(keyMaterial.provider().orElseThrow().activeKey().keyId()).isEqualTo("2");
        assertThat(keyPairsByKeyId).containsOnlyKeys("2");
        verify(vaultClient, times(1)).deleteAgreementTokenSigningKey("1");
        verify(vaultClient, never()).deleteAgreementTokenSigningKey("2");
    }

    @Test
    void aKeyWithoutAnIssueTimeIsDeletedOnceItsSuccessorHasBeenSigningForTheRetention() throws Exception {
        when(properties.keyRetention()).thenReturn(RETENTION);
        keyPairsByKeyId.put("1", new ECKeyGenerator(Curve.P_256).keyID("1").generate().toJSONString());
        var keyMaterial = newKeyMaterial();
        var provider = keyMaterial.provider().orElseThrow();
        provider.rotate();
        clock.advance(Duration.ofSeconds(60));
        provider.rotate();
        clock.advance(RETENTION);

        keyMaterial.retryOrRefresh();

        assertThat(keyPairsByKeyId).containsOnlyKeys("2", "3");
    }

    @Test
    void aKeyWhoseSuccessorHasNoIssueTimeIsNeverDeleted() throws Exception {
        when(properties.keyRetention()).thenReturn(RETENTION);
        when(properties.maxKeyAge()).thenReturn(Duration.ZERO);
        var keyMaterial = newKeyMaterial();
        keyPairsByKeyId.put("2", new ECKeyGenerator(Curve.P_256).keyID("2").generate().toJSONString());
        keyMaterial.provider().orElseThrow().refresh();
        clock.advance(Duration.ofDays(10));

        keyMaterial.retryOrRefresh();

        verify(vaultClient, never()).deleteAgreementTokenSigningKey(anyString());
        assertThat(keyPairsByKeyId).containsOnlyKeys("1", "2");
    }

    @Test
    void nothingIsDeletedWhenTheRetentionIsZero() {
        var keyMaterial = newKeyMaterial();
        storeThreeKeysTheFirstTenDaysOld(keyMaterial);
        clock.advance(Duration.ofDays(1));

        keyMaterial.retryOrRefresh();

        verify(vaultClient, never()).deleteAgreementTokenSigningKey(anyString());
        assertThat(keyPairsByKeyId).containsOnlyKeys("1", "2", "3");
    }

    @Test
    void nothingIsDeletedWhileDspIsDisabled() {
        when(properties.keyRetention()).thenReturn(RETENTION);
        var keyMaterial = newKeyMaterial();
        storeThreeKeysTheFirstTenDaysOld(keyMaterial);
        advanceUntilOnlyTheFirstKeyIsDue();
        when(proxyProperties.dspEnabled()).thenReturn(false);

        keyMaterial.retryOrRefresh();

        verify(vaultClient, never()).deleteAgreementTokenSigningKey(anyString());
        assertThat(keyPairsByKeyId).containsOnlyKeys("1", "2", "3");
    }

    @Test
    void nothingIsDeletedWhenTheRefreshFails() {
        lenient().when(properties.keyRetention()).thenReturn(RETENTION);
        var keyMaterial = newKeyMaterial();
        storeThreeKeysTheFirstTenDaysOld(keyMaterial);
        advanceUntilOnlyTheFirstKeyIsDue();
        when(vaultClient.getAgreementTokenSigningKeys()).thenThrow(new IllegalStateException("vault sealed"));

        keyMaterial.retryOrRefresh();

        verify(vaultClient, never()).deleteAgreementTokenSigningKey(anyString());
    }

    @Test
    void failedDeletionLeavesTheRefreshAndTheSnapshotIntact() {
        when(properties.keyRetention()).thenReturn(RETENTION);
        var keyMaterial = newKeyMaterial();
        storeThreeKeysTheFirstTenDaysOld(keyMaterial);
        advanceUntilOnlyTheFirstKeyIsDue();
        var provider = keyMaterial.provider().orElseThrow();
        doThrow(new IllegalStateException("403 permission denied"))
                .when(vaultClient).deleteAgreementTokenSigningKey(anyString());

        keyMaterial.retryOrRefresh();

        assertThat(keyMaterial.provider()).contains(provider);
        assertThat(provider.listKeys()).extracting(AgreementTokenKeyInfo::keyId).containsExactly("1", "2", "3");
        assertThat(provider.activeKey().keyId()).isEqualTo("3");
        assertThat(keyPairsByKeyId).containsOnlyKeys("1", "2", "3");
        verify(vaultClient, times(1)).deleteAgreementTokenSigningKey("1");
    }

    @Test
    void failedDeletionsAreWarnedAboutOnceAndThenOnlyEveryFewRefreshes() {
        when(properties.keyRetention()).thenReturn(RETENTION);
        var keyMaterial = newKeyMaterial();
        storeThreeKeysTheFirstTenDaysOld(keyMaterial);
        advanceUntilOnlyTheFirstKeyIsDue();
        doThrow(new IllegalStateException("403 permission denied"))
                .when(vaultClient).deleteAgreementTokenSigningKey(anyString());
        var ticks = AgreementTokenKeyMaterial.PRUNE_FAILURE_WARNING_TICKS;

        keyMaterial.retryOrRefresh();
        assertThat(pruneFailureWarnings()).isEqualTo(1);

        for (var tick = 2; tick <= ticks; tick++) {
            keyMaterial.retryOrRefresh();
        }
        assertThat(pruneFailureWarnings()).isEqualTo(1);
        verify(vaultClient, times(ticks)).deleteAgreementTokenSigningKey("1");

        keyMaterial.retryOrRefresh();
        assertThat(pruneFailureWarnings()).isEqualTo(2);
    }

    @Test
    void aFailureAfterARecoveredDeletionIsWarnedAboutAtOnce() {
        when(properties.keyRetention()).thenReturn(RETENTION);
        var keyMaterial = newKeyMaterial();
        storeThreeKeysTheFirstTenDaysOld(keyMaterial);
        advanceUntilOnlyTheFirstKeyIsDue();
        doThrow(new IllegalStateException("403 permission denied"))
                .when(vaultClient).deleteAgreementTokenSigningKey(anyString());
        keyMaterial.retryOrRefresh();
        assertThat(pruneFailureWarnings()).isEqualTo(1);

        removeDeletedKeysFromTheFakeSecretStore();
        keyMaterial.retryOrRefresh();
        assertThat(keyPairsByKeyId).containsOnlyKeys("2", "3");

        clock.advance(Duration.ofDays(2));
        keyMaterial.provider().orElseThrow().rotate();
        clock.advance(Duration.ofSeconds(60));
        doThrow(new IllegalStateException("403 permission denied"))
                .when(vaultClient).deleteAgreementTokenSigningKey(anyString());
        keyMaterial.retryOrRefresh();

        assertThat(pruneFailureWarnings()).isEqualTo(2);
    }

    @Test
    void aSupersededKeyKeepsVerifyingItsTokensUntilTheRetentionHasPassedThenIsDeleted() {
        when(properties.keyRetention()).thenReturn(RETENTION);
        when(properties.maxKeyAge()).thenReturn(Duration.ofDays(7));
        var keyMaterial = newKeyMaterial();
        var provider = keyMaterial.provider().orElseThrow();
        clock.advance(Duration.ofDays(8));
        keyMaterial.retryOrRefresh();
        assertThat(keyPairsByKeyId).containsOnlyKeys("1", "2");
        clock.advance(Duration.ofSeconds(29));
        keyMaterial.retryOrRefresh();
        assertThat(provider.activeKey().keyId()).isEqualTo("1");
        var token = new AgreementTokenMinter(provider, TOKEN_PROPERTIES, clock).mint(GRANT);
        var verifier = new AgreementTokenVerifier(provider, TOKEN_PROPERTIES,
                Clock.fixed(clock.instant().plusSeconds(1), ZoneOffset.UTC));

        clock.advance(Duration.ofSeconds(1));
        keyMaterial.retryOrRefresh();
        assertThat(provider.activeKey().keyId()).isEqualTo("2");
        assertThat(verifier.verify(token, CONTEXT)).isInstanceOf(AgreementTokenVerificationResult.Valid.class);
        assertThat(keyPairsByKeyId).containsOnlyKeys("1", "2");

        clock.advance(RETENTION);
        keyMaterial.retryOrRefresh();
        assertThat(verifier.verify(token, CONTEXT)).isInstanceOf(AgreementTokenVerificationResult.Valid.class);
        assertThat(keyPairsByKeyId).containsOnlyKeys("1", "2");

        clock.advance(Duration.ofSeconds(1));
        keyMaterial.retryOrRefresh();
        assertThat(keyPairsByKeyId).containsOnlyKeys("2");
        keyMaterial.retryOrRefresh();
        assertThat(verifier.verify(token, CONTEXT)).isInstanceOfSatisfying(AgreementTokenVerificationResult.Rejected.class,
                rejected -> assertThat(rejected.reason()).isEqualTo(AgreementTokenRejectionReason.UNKNOWN_KEY_ID));
    }

    /**
     * Leaves keys 1 to 3 in the store: key 1 was issued ten days before key 2, key 2 two minutes and key 3 one
     * minute before the clock's current time. Key 3 is the highest id and the signing key, key 1 stopped signing
     * 90 seconds ago and key 2 30 seconds ago.
     */
    private void storeThreeKeysTheFirstTenDaysOld(AgreementTokenKeyMaterial keyMaterial) {
        var provider = keyMaterial.provider().orElseThrow();
        clock.advance(Duration.ofDays(10));
        provider.rotate();
        clock.advance(Duration.ofSeconds(60));
        provider.rotate();
        clock.advance(Duration.ofSeconds(60));
        provider.refresh();
        assertThat(provider.activeKey().keyId()).isEqualTo("3");
    }

    /**
     * Moves the clock so that key 1 stopped signing slightly more than the retention ago and key 2 slightly less.
     */
    private void advanceUntilOnlyTheFirstKeyIsDue() {
        clock.advance(RETENTION.minusSeconds(60));
    }

    private long pruneFailureWarnings() {
        return logHandler.records.stream()
                .filter(logRecord -> logRecord.getLevel().intValue() == Level.WARNING.intValue())
                .filter(logRecord -> logRecord.getMessage().contains("Failed to delete agreement-token signing key"))
                .count();
    }

    private static final class RecordingHandler extends Handler {
        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord logRecord) {
            records.add(logRecord);
        }

        @Override
        public void flush() {
            // no-op, records are kept in memory
        }

        @Override
        public void close() {
            // no-op, nothing to release
        }
    }
}
