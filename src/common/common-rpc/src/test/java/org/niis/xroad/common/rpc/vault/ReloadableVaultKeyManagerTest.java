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

import io.github.resilience4j.retry.Retry;
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
import org.niis.xroad.common.properties.CommonRpcProperties;
import org.niis.xroad.common.properties.ConfigUtils;
import org.niis.xroad.common.tls.vault.VaultKeyClient;

import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class ReloadableVaultKeyManagerTest {

    private CommonRpcProperties.CertificateProvisionProperties certificateProvisionProperties;
    @Mock
    private VaultKeyClient vaultKeyClient;
    @Mock
    private ScheduledExecutorService scheduler;


    @Spy
    private AdvancedTlsX509KeyManager keyManager = new AdvancedTlsX509KeyManager();

    private Retry retryInstance;

    private AdvancedTlsX509TrustManager trustManager;

    private ReloadableVaultKeyManager reloadableVaultKeyManager;

    @Captor
    private ArgumentCaptor<Runnable> runnableCaptor;
    @Captor
    private ArgumentCaptor<Long> delayCaptor;
    @Captor
    private ArgumentCaptor<TimeUnit> timeUnitCaptor;
    @Captor
    private ArgumentCaptor<X509Certificate[]> trustCertsCaptor;
    @Captor
    private ArgumentCaptor<X509Certificate[]> identityCertsCaptor;
    @Captor
    private ArgumentCaptor<PrivateKey> privateKeyCaptor;


    @BeforeEach
    void setUp() throws CertificateException {
        certificateProvisionProperties = ConfigUtils.initConfiguration(CommonRpcProperties.class,
                Map.of("xroad.common.rpc.certificate-provisioning.retry-max-attempts", "2",
                        "xroad.common.rpc.certificate-provisioning.retry-base-delay", "1s",
                        "xroad.common.rpc.certificate-provisioning.refresh-interval", "60s",
                        "xroad.common.rpc.certificate-provisioning.retry-exponential-backoff-multiplier", "1.0")
        ).certificateProvisioning();

        trustManager = spy(AdvancedTlsX509TrustManager.newBuilder()
                .setVerification(AdvancedTlsX509TrustManager.Verification.CERTIFICATE_AND_HOST_NAME_VERIFICATION)
                .build());


        retryInstance = spy(ReloadableVaultKeyManager.createRetryInstance(certificateProvisionProperties));
        reloadableVaultKeyManager = new ReloadableVaultKeyManager(certificateProvisionProperties, vaultKeyClient, trustManager,
                keyManager, scheduler, retryInstance);

    }

    @Test
    void reloadShouldSucceedOnFirstAttempt() throws Throwable {
        // Arrange
        var mockResult = mock(VaultKeyClient.VaultKeyData.class);
        var mockIdentityCerts = new X509Certificate[]{mock(X509Certificate.class)};
        var mockTrustCerts = new X509Certificate[]{mock(X509Certificate.class)};
        var mockPrivateKey = mock(PrivateKey.class);

        when(mockResult.identityCertChain()).thenReturn(mockIdentityCerts);
        when(mockResult.trustCerts()).thenReturn(mockTrustCerts);
        when(mockResult.identityPrivateKey()).thenReturn(mockPrivateKey);
        when(vaultKeyClient.provisionNewCerts()).thenReturn(mockResult);

        // Act
        reloadableVaultKeyManager.reload();

        // Assert
        // Verify provisionNewCerts was called exactly once
        verify(vaultKeyClient, times(1)).provisionNewCerts();

        // Verify keyManager and trustManager were updated with the results
        verify(keyManager).updateIdentityCredentials(identityCertsCaptor.capture(), privateKeyCaptor.capture());
        verify(trustManager).updateTrustCredentials(trustCertsCaptor.capture());

        assertThat(identityCertsCaptor.getValue()).isEqualTo(mockIdentityCerts);
        assertThat(privateKeyCaptor.getValue()).isEqualTo(mockPrivateKey);
        assertThat(trustCertsCaptor.getValue()).isEqualTo(mockTrustCerts);

        // Verify the next reload is scheduled
        verify(scheduler).schedule(runnableCaptor.capture(), delayCaptor.capture(), timeUnitCaptor.capture());
        assertThat(delayCaptor.getValue()).isEqualTo(60L);
        assertThat(timeUnitCaptor.getValue()).isEqualTo(TimeUnit.SECONDS);
    }

    @Test
    void reloadShouldSucceedOnRetry() throws Throwable {
        // Arrange
        var mockResult = mock(VaultKeyClient.VaultKeyData.class);
        var mockIdentityCerts = new X509Certificate[]{mock(X509Certificate.class)};
        var mockTrustCerts = new X509Certificate[]{mock(X509Certificate.class)};
        var mockPrivateKey = mock(PrivateKey.class);

        when(mockResult.identityCertChain()).thenReturn(mockIdentityCerts);
        when(mockResult.trustCerts()).thenReturn(mockTrustCerts);
        when(mockResult.identityPrivateKey()).thenReturn(mockPrivateKey);

        // Fail first time, succeed second time
        when(vaultKeyClient.provisionNewCerts())
                .thenThrow(new RuntimeException("Temporary failure"))
                .thenReturn(mockResult);

        // Act
        reloadableVaultKeyManager.reload();

        // Assert
        // Verify provisionNewCerts was called exactly twice (initial + 1 retry)
        verify(vaultKeyClient, times(2)).provisionNewCerts();

        // Verify keyManager and trustManager were updated with the results from the successful call
        verify(keyManager).updateIdentityCredentials(identityCertsCaptor.capture(), privateKeyCaptor.capture());
        verify(trustManager).updateTrustCredentials(trustCertsCaptor.capture());

        assertThat(identityCertsCaptor.getValue()).isEqualTo(mockIdentityCerts);
        assertThat(privateKeyCaptor.getValue()).isEqualTo(mockPrivateKey);
        assertThat(trustCertsCaptor.getValue()).isEqualTo(mockTrustCerts);

        // Verify the next reload is scheduled
        verify(scheduler).schedule(runnableCaptor.capture(), delayCaptor.capture(), timeUnitCaptor.capture());
        assertThat(delayCaptor.getValue()).isEqualTo(60L);
        assertThat(timeUnitCaptor.getValue()).isEqualTo(TimeUnit.SECONDS);


        // Verify the retry metrics (optional but good)
        assertThat(retryInstance.getMetrics().getNumberOfSuccessfulCallsWithRetryAttempt()).isOne();
        assertThat(retryInstance.getMetrics().getNumberOfFailedCallsWithRetryAttempt()).isZero();
    }

    @Test
    void reloadShouldFailAfterExhaustingRetries() throws Throwable {
        // Arrange
        var failureException = new RuntimeException("Persistent failure");

        // Fail all attempts (initial + 2 retries = 3)
        when(vaultKeyClient.provisionNewCerts())
                .thenThrow(failureException)
                .thenThrow(failureException)
                .thenThrow(failureException);

        // Act
        // The reload method catches the exception internally after retries are exhausted
        reloadableVaultKeyManager.reload();

        // Assert
        // Verify provisionNewCerts was called the maximum number of times (initial + max retries)
        int expectedAttempts = certificateProvisionProperties.retryMaxAttempts() + 1;
        verify(vaultKeyClient, times(expectedAttempts)).provisionNewCerts();

        // Verify keyManager and trustManager were NEVER updated
        verify(keyManager, never()).updateIdentityCredentials((X509Certificate[]) any(), any());
        verify(trustManager, never()).updateTrustCredentials((X509Certificate[]) any());

        // Verify the next reload is scheduled even after failure
        verify(scheduler).schedule(runnableCaptor.capture(), delayCaptor.capture(), timeUnitCaptor.capture());
        assertThat(delayCaptor.getValue()).isEqualTo(60L); // Still schedules the next attempt
        assertThat(timeUnitCaptor.getValue()).isEqualTo(TimeUnit.SECONDS);

        assertThat(retryInstance.getMetrics().getNumberOfSuccessfulCallsWithoutRetryAttempt()).isZero();
        assertThat(retryInstance.getMetrics().getNumberOfSuccessfulCallsWithRetryAttempt()).isZero();
        assertThat(retryInstance.getMetrics().getNumberOfFailedCallsWithoutRetryAttempt()).isZero(); // Failed *after* retries
        assertThat(retryInstance.getMetrics().getNumberOfFailedCallsWithRetryAttempt()).isOne();
    }

}
