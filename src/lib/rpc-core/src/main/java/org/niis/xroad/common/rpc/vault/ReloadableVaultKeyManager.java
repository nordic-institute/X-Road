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
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.grpc.util.AdvancedTlsX509KeyManager;
import io.grpc.util.AdvancedTlsX509TrustManager;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.rpc.RpcProperties;
import org.niis.xroad.common.rpc.VaultKeyProvider;
import org.niis.xroad.common.vault.VaultKeyClient;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

import java.security.cert.CertificateException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static io.github.resilience4j.core.IntervalFunction.ofExponentialBackoff;

/**
 * TODO XRDDEV-2880 refresh could still fail, even after retries. This should be handled with service health checks.
 */
@Slf4j
public class ReloadableVaultKeyManager implements VaultKeyProvider {
    private static final String RETRY_INSTANCE_NAME = "vaultReloadRetry";

    private final RpcProperties.RpcCertificateProvisioningProperties certificateProvisionProperties;
    private final ScheduledExecutorService scheduler;
    private final VaultKeyClient vaultKeyClient;
    private final AdvancedTlsX509TrustManager trustManager;
    private final AdvancedTlsX509KeyManager keyManager;

    private final Retry retryInstance;

    public ReloadableVaultKeyManager(RpcProperties.RpcCertificateProvisioningProperties certificateProvisioningProperties,
                                     VaultKeyClient vaultKeyClient, AdvancedTlsX509TrustManager trustManager,
                                     AdvancedTlsX509KeyManager keyManager, ScheduledExecutorService scheduler, Retry retryInstance) {
        this.certificateProvisionProperties = certificateProvisioningProperties;
        this.vaultKeyClient = vaultKeyClient;
        this.trustManager = trustManager;
        this.keyManager = keyManager;
        this.scheduler = scheduler;
        this.retryInstance = retryInstance;
    }

    @Override
    public void init() {
        var startTime = System.currentTimeMillis();
        log.info("Initializing reloadable vault key manager..");
        reload();
        log.info("Reloadable vault key manager initialized in {} ms", System.currentTimeMillis() - startTime);
    }

    @Override
    public void shutdown() {
        scheduler.shutdown();
    }

    void reload() {
        try {
            Retry.decorateCheckedRunnable(retryInstance, () -> {
                var result = vaultKeyClient.provisionNewCerts();

                keyManager.updateIdentityCredentials(
                        result.identityCertChain(),
                        result.identityPrivateKey());

                trustManager.updateTrustCredentials(result.trustCerts());
            }).run();
        } catch (Throwable e) {
            log.error("Error while reloading vault key manager", e);
        } finally {
            log.debug("Scheduling next reload in {}", certificateProvisionProperties.refreshInterval());
            scheduler.schedule(this::reload, certificateProvisionProperties.refreshInterval().toSeconds(), TimeUnit.SECONDS);
        }
    }

    @Override
    public KeyManager getKeyManager() {
        return keyManager;
    }

    @Override
    public TrustManager getTrustManager() {
        return trustManager;
    }

    static Retry createRetryInstance(RpcProperties.RpcCertificateProvisioningProperties certificateProvisioningProperties) {
        var retryInterval = ofExponentialBackoff(certificateProvisioningProperties.retryDelay(),
                certificateProvisioningProperties.retryExponentialBackoffMultiplier());

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(certificateProvisioningProperties.retryMaxAttempts() + 1)
                .intervalFunction(retryInterval)
                .retryExceptions(Exception.class)
                .failAfterMaxAttempts(true)
                .build();
        var retryRegistry = RetryRegistry.of(retryConfig);

        return retryRegistry.retry(RETRY_INSTANCE_NAME, retryConfig);
    }

    public static ReloadableVaultKeyManager withDefaults(
            RpcProperties.RpcCertificateProvisioningProperties certificateProvisionProperties,
            VaultKeyClient vaultKeyClient) throws CertificateException {
        var trustStore = AdvancedTlsX509TrustManager.newBuilder()
                .setVerification(AdvancedTlsX509TrustManager.Verification.CERTIFICATE_AND_HOST_NAME_VERIFICATION)
                .build();
        var keyManager = new AdvancedTlsX509KeyManager();

        var scheduler = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());

        var retryInstance = createRetryInstance(certificateProvisionProperties);
        retryInstance.getEventPublisher().onRetry(event ->
                log.warn("Retrying provision certificates from secret store. Event: {}", event));

        return new ReloadableVaultKeyManager(certificateProvisionProperties, vaultKeyClient,
                trustStore, keyManager, scheduler, retryInstance);

    }
}
