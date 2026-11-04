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

import io.github.resilience4j.core.IntervalFunction;
import io.grpc.util.AdvancedTlsX509KeyManager;
import io.grpc.util.AdvancedTlsX509TrustManager;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.rpc.RpcProperties;
import org.niis.xroad.common.rpc.VaultKeyProvider;
import org.niis.xroad.common.vault.VaultKeyClient;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Loads the gRPC TLS key and trust material from the secret store. Every reload makes one load attempt.
 * After a success the next reload runs after the refresh interval. After a failure it runs after an exponentially
 * growing, capped delay, and the previously loaded material stays in place.
 */
@Slf4j
public class ReloadableVaultKeyManager implements VaultKeyProvider {

    private final RpcProperties.RpcCertificateProvisioningProperties certificateProvisionProperties;
    private final ScheduledExecutorService scheduler;
    private final VaultKeyClient vaultKeyClient;
    private final AdvancedTlsX509TrustManager trustManager;
    private final AdvancedTlsX509KeyManager keyManager;
    private final IntervalFunction failureBackoff;

    private int consecutiveFailures;

    public ReloadableVaultKeyManager(RpcProperties.RpcCertificateProvisioningProperties certificateProvisioningProperties,
                                     VaultKeyClient vaultKeyClient, AdvancedTlsX509TrustManager trustManager,
                                     AdvancedTlsX509KeyManager keyManager, ScheduledExecutorService scheduler) {
        this.certificateProvisionProperties = certificateProvisioningProperties;
        this.vaultKeyClient = vaultKeyClient;
        this.trustManager = trustManager;
        this.keyManager = keyManager;
        this.scheduler = scheduler;
        this.failureBackoff = IntervalFunction.ofExponentialBackoff(
                certificateProvisioningProperties.waitDuration(),
                certificateProvisioningProperties.exponentialBackoffMultiplier(),
                certificateProvisioningProperties.exponentialMaxWaitDuration());
    }

    @Override
    public void init() {
        log.info("Initializing reloadable vault key manager..");
        reload();
    }

    @Override
    public void shutdown() {
        scheduler.shutdown();
    }

    void reload() {
        var nextDelay = loadKeyMaterial();
        scheduler.schedule(this::reload, nextDelay.toMillis(), TimeUnit.MILLISECONDS);
    }

    private Duration loadKeyMaterial() {
        try {
            var startTime = System.currentTimeMillis();
            var result = vaultKeyClient.provisionNewCerts();

            keyManager.updateIdentityCredentials(
                    result.identityCertChain(),
                    result.identityPrivateKey());

            trustManager.updateTrustCredentials(result.trustCerts());

            consecutiveFailures = 0;
            var delay = certificateProvisionProperties.refreshInterval();
            log.info("Reloadable vault key manager loaded key material in {} ms, next reload in {}",
                    System.currentTimeMillis() - startTime, delay);
            return delay;
        } catch (Throwable e) {
            consecutiveFailures++;
            var delay = Duration.ofMillis(failureBackoff.apply(consecutiveFailures));
            log.error("Failed to load key material from secret store (consecutive failures: {}), next attempt in {}",
                    consecutiveFailures, delay, e);
            return delay;
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

    public static ReloadableVaultKeyManager withDefaults(
            RpcProperties.RpcCertificateProvisioningProperties certificateProvisionProperties,
            VaultKeyClient vaultKeyClient) throws CertificateException {
        var trustStore = AdvancedTlsX509TrustManager.newBuilder()
                .setVerification(AdvancedTlsX509TrustManager.Verification.CERTIFICATE_AND_HOST_NAME_VERIFICATION)
                .build();
        var keyManager = new AdvancedTlsX509KeyManager();

        var scheduler = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());

        return new ReloadableVaultKeyManager(certificateProvisionProperties, vaultKeyClient,
                trustStore, keyManager, scheduler);
    }
}
