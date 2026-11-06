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
package org.niis.xroad.common.rpc.vault;

import io.grpc.util.AdvancedTlsX509KeyManager;
import io.grpc.util.AdvancedTlsX509TrustManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.properties.util.DurationConverter;
import org.niis.xroad.common.rpc.RpcProperties;
import org.niis.xroad.common.rpc.test.TestCa;
import org.niis.xroad.common.vault.VaultKeyClient;

import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReloadableVaultKeyManagerTest {

    private static final long REFRESH_INTERVAL_MILLIS = 60_000L;
    private static final long WAIT_DURATION_MILLIS = 1_000L;
    private static final long MAX_WAIT_DURATION_MILLIS = 8_000L;

    private RpcProperties.RpcCertificateProvisioningProperties certificateProvisionProperties;
    @Mock
    private VaultKeyClient vaultKeyClient;
    @Mock
    private ScheduledExecutorService scheduler;

    @Spy
    private AdvancedTlsX509KeyManager keyManager = new AdvancedTlsX509KeyManager();

    private AdvancedTlsX509TrustManager trustManager;

    private ReloadableVaultKeyManager reloadableVaultKeyManager;

    @Captor
    private ArgumentCaptor<Runnable> runnableCaptor;
    @Captor
    private ArgumentCaptor<Long> delayCaptor;
    @Captor
    private ArgumentCaptor<X509Certificate[]> trustCertsCaptor;
    @Captor
    private ArgumentCaptor<X509Certificate[]> identityCertsCaptor;
    @Captor
    private ArgumentCaptor<PrivateKey> privateKeyCaptor;

    @BeforeEach
    void setUp() throws CertificateException {
        certificateProvisionProperties = new TestCertificateProvisioningProperties();

        trustManager = spy(AdvancedTlsX509TrustManager.newBuilder()
                .setVerification(AdvancedTlsX509TrustManager.Verification.CERTIFICATE_AND_HOST_NAME_VERIFICATION)
                .build());

        reloadableVaultKeyManager = new ReloadableVaultKeyManager(certificateProvisionProperties, vaultKeyClient, trustManager,
                keyManager, scheduler);
    }

    @Test
    void reloadShouldSucceedOnFirstAttempt() throws Throwable {
        var mockResult = mock(VaultKeyClient.VaultKeyData.class);
        var mockIdentityCerts = new X509Certificate[]{mock(X509Certificate.class)};
        var mockTrustCerts = new X509Certificate[]{mock(X509Certificate.class)};
        var mockPrivateKey = mock(PrivateKey.class);

        when(mockResult.identityCertChain()).thenReturn(mockIdentityCerts);
        when(mockResult.trustCerts()).thenReturn(mockTrustCerts);
        when(mockResult.identityPrivateKey()).thenReturn(mockPrivateKey);
        when(vaultKeyClient.provisionNewCerts()).thenReturn(mockResult);

        reloadableVaultKeyManager.reload();

        verify(vaultKeyClient, times(1)).provisionNewCerts();

        verify(keyManager).updateIdentityCredentials(identityCertsCaptor.capture(), privateKeyCaptor.capture());
        verify(trustManager).updateTrustCredentials(trustCertsCaptor.capture());

        assertThat(identityCertsCaptor.getValue()).isEqualTo(mockIdentityCerts);
        assertThat(privateKeyCaptor.getValue()).isEqualTo(mockPrivateKey);
        assertThat(trustCertsCaptor.getValue()).isEqualTo(mockTrustCerts);

        assertThat(scheduledDelays(1)).containsExactly(REFRESH_INTERVAL_MILLIS);
    }

    @Test
    void firstFailedLoadShouldScheduleWaitDurationAndLeaveManagersUntouched() throws Exception {
        when(vaultKeyClient.provisionNewCerts()).thenThrow(new IllegalStateException("secret store unreachable"));

        reloadableVaultKeyManager.reload();

        verify(vaultKeyClient, times(1)).provisionNewCerts();
        verify(keyManager, never()).updateIdentityCredentials((X509Certificate[]) any(), any());
        verify(trustManager, never()).updateTrustCredentials((X509Certificate[]) any());
        assertThat(scheduledDelays(1)).containsExactly(WAIT_DURATION_MILLIS);
    }

    @Test
    void errorThrownByLoadShouldStillScheduleNextReloadAtWaitDuration() throws Exception {
        when(vaultKeyClient.provisionNewCerts()).thenThrow(new LinkageError("class initialization failed"));

        reloadableVaultKeyManager.reload();

        verify(keyManager, never()).updateIdentityCredentials((X509Certificate[]) any(), any());
        assertThat(scheduledDelays(1)).containsExactly(WAIT_DURATION_MILLIS);
    }

    @Test
    void consecutiveFailuresShouldGrowByMultiplierAndNeverExceedMaxWaitDuration() throws Exception {
        when(vaultKeyClient.provisionNewCerts()).thenThrow(new IllegalStateException("secret store unreachable"));

        for (int i = 0; i < 6; i++) {
            reloadableVaultKeyManager.reload();
        }

        assertThat(scheduledDelays(6)).containsExactly(
                WAIT_DURATION_MILLIS,
                2 * WAIT_DURATION_MILLIS,
                4 * WAIT_DURATION_MILLIS,
                MAX_WAIT_DURATION_MILLIS,
                MAX_WAIT_DURATION_MILLIS,
                MAX_WAIT_DURATION_MILLIS);
    }

    @Test
    void successAfterFailuresShouldScheduleRefreshIntervalAndResetBackoff() throws Exception {
        var loaded = vaultKeyData();
        when(vaultKeyClient.provisionNewCerts())
                .thenThrow(new IllegalStateException("secret store unreachable"))
                .thenThrow(new IllegalStateException("secret store unreachable"))
                .thenReturn(loaded)
                .thenThrow(new IllegalStateException("secret store unreachable"));

        for (int i = 0; i < 4; i++) {
            reloadableVaultKeyManager.reload();
        }

        assertThat(scheduledDelays(4)).containsExactly(
                WAIT_DURATION_MILLIS,
                2 * WAIT_DURATION_MILLIS,
                REFRESH_INTERVAL_MILLIS,
                WAIT_DURATION_MILLIS);
    }

    @Test
    void failedPeriodicRefreshShouldKeepLoadedMaterialAndScheduleBackoff() throws Exception {
        var loaded = vaultKeyData();
        when(vaultKeyClient.provisionNewCerts())
                .thenReturn(loaded)
                .thenThrow(new IllegalStateException("secret store unreachable"));

        reloadableVaultKeyManager.reload();
        reloadableVaultKeyManager.reload();

        verify(keyManager, times(1)).updateIdentityCredentials((X509Certificate[]) any(), any());
        verify(trustManager, times(1)).updateTrustCredentials((X509Certificate[]) any());
        assertThat(scheduledDelays(2)).containsExactly(REFRESH_INTERVAL_MILLIS, WAIT_DURATION_MILLIS);
    }

    @Test
    void scheduledReloadShouldRunAnotherLoadAttempt() throws Exception {
        var loaded = vaultKeyData();
        when(vaultKeyClient.provisionNewCerts())
                .thenThrow(new IllegalStateException("secret store unreachable"))
                .thenReturn(loaded);

        reloadableVaultKeyManager.init();
        verify(scheduler).schedule(runnableCaptor.capture(), eq(WAIT_DURATION_MILLIS), eq(TimeUnit.MILLISECONDS));
        runnableCaptor.getValue().run();

        verify(vaultKeyClient, times(2)).provisionNewCerts();
        verify(keyManager).updateIdentityCredentials((X509Certificate[]) any(), any());
        assertThat(scheduledDelays(2)).containsExactly(WAIT_DURATION_MILLIS, REFRESH_INTERVAL_MILLIS);
    }

    @Test
    void initFailingShouldScheduleNextReloadBeforeRefreshInterval() throws Exception {
        when(vaultKeyClient.provisionNewCerts()).thenThrow(new IllegalStateException("secret store unreachable"));

        reloadableVaultKeyManager.init();

        verify(keyManager, never()).updateIdentityCredentials((X509Certificate[]) any(), any());
        verify(scheduler).schedule(runnableCaptor.capture(), delayCaptor.capture(), any(TimeUnit.class));
        assertThat(delayCaptor.getValue()).isLessThan(REFRESH_INTERVAL_MILLIS);
    }

    @Test
    void initWithUnreachableSecretStoreShouldMakeSingleCallAndScheduleExactlyOneReload() throws Exception {
        when(vaultKeyClient.provisionNewCerts()).thenThrow(new IllegalStateException("secret store unreachable"));

        reloadableVaultKeyManager.init();

        verify(vaultKeyClient, times(1)).provisionNewCerts();
        verify(scheduler, times(1)).schedule(any(Runnable.class), any(Long.class), any(TimeUnit.class));
        verifyNoMoreInteractions(vaultKeyClient, scheduler);
    }

    private List<Long> scheduledDelays(int expectedReloads) {
        verify(scheduler, times(expectedReloads)).schedule(any(Runnable.class), delayCaptor.capture(), eq(TimeUnit.MILLISECONDS));
        return delayCaptor.getAllValues();
    }

    private static VaultKeyClient.VaultKeyData vaultKeyData() {
        var result = mock(VaultKeyClient.VaultKeyData.class);
        when(result.identityCertChain()).thenReturn(new X509Certificate[]{mock(X509Certificate.class)});
        when(result.trustCerts()).thenReturn(new X509Certificate[]{mock(X509Certificate.class)});
        when(result.identityPrivateKey()).thenReturn(mock(PrivateKey.class));
        return result;
    }

    @Test
    void reloadUpdatesTheVerifyingTrustManagerTogetherWithTheSharedOne() throws Exception {
        var firstCa = TestCa.selfSigned("First CA").certificate();
        var secondCa = TestCa.selfSigned("Second CA").certificate();
        var verifyingTrustManager = reloadableVaultKeyManager.createVerifyingTrustManager(
                mock(AdvancedTlsX509TrustManager.SslSocketAndEnginePeerVerifier.class));

        provisionTrustCerts(firstCa);
        reloadableVaultKeyManager.reload();

        assertThat(verifyingTrustManager.getAcceptedIssuers()).containsExactly(firstCa);
        assertThat(verifyingTrustManager.getAcceptedIssuers()).containsExactly(trustManager.getAcceptedIssuers());

        provisionTrustCerts(secondCa);
        reloadableVaultKeyManager.reload();

        assertThat(verifyingTrustManager.getAcceptedIssuers()).containsExactly(secondCa);
        assertThat(verifyingTrustManager.getAcceptedIssuers()).containsExactly(trustManager.getAcceptedIssuers());
    }

    @Test
    void verifyingTrustManagerCreatedAfterAReloadStartsWithTheCurrentTrustCerts() throws Exception {
        var ca = TestCa.selfSigned("CA").certificate();
        provisionTrustCerts(ca);
        reloadableVaultKeyManager.reload();

        var verifyingTrustManager = reloadableVaultKeyManager.createVerifyingTrustManager(
                mock(AdvancedTlsX509TrustManager.SslSocketAndEnginePeerVerifier.class));

        assertThat(verifyingTrustManager.getAcceptedIssuers()).containsExactly(ca);
    }

    private void provisionTrustCerts(X509Certificate... trustCerts) throws Exception {
        var result = mock(VaultKeyClient.VaultKeyData.class);
        when(result.identityCertChain()).thenReturn(new X509Certificate[] {mock(X509Certificate.class)});
        var privateKey = mock(PrivateKey.class);
        when(result.identityPrivateKey()).thenReturn(privateKey);
        when(result.trustCerts()).thenReturn(trustCerts);
        when(vaultKeyClient.provisionNewCerts()).thenReturn(result);
    }

    private static final class TestCertificateProvisioningProperties implements RpcProperties.RpcCertificateProvisioningProperties {

        @Override
        public Duration refreshInterval() {
            return Duration.ofMillis(REFRESH_INTERVAL_MILLIS);
        }

        @Override
        public Duration waitDuration() {
            return Duration.ofMillis(WAIT_DURATION_MILLIS);
        }

        @Override
        public Double exponentialBackoffMultiplier() {
            return 2.0;
        }

        @Override
        public Duration exponentialMaxWaitDuration() {
            return Duration.ofMillis(MAX_WAIT_DURATION_MILLIS);
        }

        @Override
        public String issuanceRoleName() {
            return DEFAULT_ISSUANCE_ROLE_NAME;
        }

        @Override
        public String commonName() {
            return DEFAULT_COMMON_NAME;
        }

        @Override
        public List<String> altNames() {
            return List.of();
        }

        @Override
        public List<String> ipSubjectAltNames() {
            return List.of();
        }

        @Override
        public Duration ttl() {
            return DurationConverter.parseDuration(DEFAULT_TTL);
        }

        @Override
        public String secretStorePkiPath() {
            return DEFAULT_SECRET_STORE_PKI_PATH;
        }
    }
}
