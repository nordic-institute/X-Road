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
package org.niis.xroad.common.vault.quarkus;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.vault.client.VaultClient;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.interceptor.Interceptor;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.keys.CommonConfigKeys;
import org.niis.xroad.common.vault.VaultTokenKeepalive;

/**
 * Renews the secret store token shared by all services of a node at startup and every configured interval. The
 * bean is discovered through the library's Jandex index, and schedules the renewal only where the scheduler is
 * enabled ({@code quarkus.scheduler.enabled=false} switches it off).
 */
@ApplicationScoped
class QuarkusVaultTokenKeepalive {

    private static final String JOB_ID = "vault-token-keepalive";

    private final Scheduler scheduler;
    private final Scheduled.ApplicationNotRunning applicationNotRunning;
    private final boolean schedulerEnabled;
    private final Instance<VaultClient> vaultClient;
    private final Instance<XRoadConfig> config;

    QuarkusVaultTokenKeepalive(Scheduler scheduler,
                               Scheduled.ApplicationNotRunning applicationNotRunning,
                               @ConfigProperty(name = "quarkus.scheduler.enabled", defaultValue = "true")
                               boolean schedulerEnabled,
                               Instance<VaultClient> vaultClient,
                               Instance<XRoadConfig> config) {
        this.scheduler = scheduler;
        this.applicationNotRunning = applicationNotRunning;
        this.schedulerEnabled = schedulerEnabled;
        this.vaultClient = vaultClient;
        this.config = config;
    }

    void schedule(@Observes @Priority(Interceptor.Priority.LIBRARY_AFTER) StartupEvent event) {
        if (!schedulerEnabled) {
            return;
        }
        var xRoadConfig = config.get();
        var client = vaultClient.get();
        var keepalive = new VaultTokenKeepalive(QuarkusVaultFailures::isRetryable,
                xRoadConfig.value(CommonConfigKeys.VAULT_RETRY_BUDGET),
                () -> renewSelf(client));
        scheduler.newJob(JOB_ID)
                .setInterval(xRoadConfig.value(CommonConfigKeys.VAULT_TOKEN_RENEWAL_INTERVAL).toString())
                .setTask(_ -> keepalive.renewOnce())
                .setConcurrentExecution(Scheduled.ConcurrentExecution.SKIP)
                .setSkipPredicate(applicationNotRunning)
                .schedule();
    }

    private static void renewSelf(VaultClient vaultClient) {
        vaultClient.auth().token().renewSelf(null).toCompletableFuture().join();
    }
}
