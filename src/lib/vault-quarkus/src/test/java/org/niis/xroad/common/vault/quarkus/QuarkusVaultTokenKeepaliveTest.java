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
import io.quarkus.scheduler.Scheduler.JobDefinition;
import io.quarkus.vault.client.VaultClient;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.keys.CommonConfigKeys;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class QuarkusVaultTokenKeepaliveTest {

    private final Scheduler scheduler = mock(Scheduler.class);
    private final Instance<VaultClient> vaultClient = instance();
    private final Instance<XRoadConfig> config = instance();
    private final Instance<VaultTokenRenewalPolicy> renewalPolicy = instance();
    private final XRoadConfig xRoadConfig = mock(XRoadConfig.class);
    private final JobDefinition<?> job = mock(JobDefinition.class, RETURNS_SELF);

    @BeforeEach
    void setUp() {
        when(config.get()).thenReturn(xRoadConfig);
        when(xRoadConfig.value(CommonConfigKeys.VAULT_RETRY_BUDGET)).thenReturn(Duration.ofSeconds(1));
        when(xRoadConfig.value(CommonConfigKeys.VAULT_TOKEN_RENEWAL_INTERVAL)).thenReturn(Duration.ofHours(1));
        doReturn(job).when(scheduler).newJob(anyString());
    }

    @Test
    void shouldNotResolveConfigOrClientWhenSchedulerIsDisabled() {
        when(renewalPolicy.isUnsatisfied()).thenReturn(true);

        keepalive(false).schedule(new StartupEvent());

        verifyNoInteractions(config, vaultClient, scheduler);
    }

    @Test
    void shouldScheduleRenewalWhenNoPolicyExists() {
        when(renewalPolicy.isUnsatisfied()).thenReturn(true);

        keepalive(true).schedule(new StartupEvent());

        verifyRenewalScheduled();
    }

    @Test
    void shouldScheduleRenewalWhenPolicyRequiresRenewal() {
        givenPolicy(true);

        keepalive(true).schedule(new StartupEvent());

        verifyRenewalScheduled();
    }

    @Test
    void shouldNotTouchConfigClientOrSchedulerWhenPolicyDoesNotRequireRenewal() {
        givenPolicy(false);

        keepalive(true).schedule(new StartupEvent());

        verifyNoInteractions(config, vaultClient, scheduler);
    }

    private void verifyRenewalScheduled() {
        verify(config).get();
        verify(vaultClient).get();
        verify(scheduler).newJob("vault-token-keepalive");
        verify(job).schedule();
    }

    private void givenPolicy(boolean required) {
        when(renewalPolicy.isUnsatisfied()).thenReturn(false);
        when(renewalPolicy.get()).thenReturn(() -> required);
    }

    private QuarkusVaultTokenKeepalive keepalive(boolean schedulerEnabled) {
        return new QuarkusVaultTokenKeepalive(scheduler, mock(Scheduled.ApplicationNotRunning.class),
                schedulerEnabled, vaultClient, config, renewalPolicy);
    }

    @SuppressWarnings("unchecked")
    private static <T> Instance<T> instance() {
        return mock(Instance.class);
    }
}
