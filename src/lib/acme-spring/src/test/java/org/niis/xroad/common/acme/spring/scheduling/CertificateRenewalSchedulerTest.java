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
package org.niis.xroad.common.acme.spring.scheduling;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.acme.config.AcmeSchedulingProperties;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CertificateRenewalSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final int RETRY_DELAY = 10;
    private static final int RENEWAL_INTERVAL = 3600;

    @Mock
    private AcmeRenewalWorker acmeRenewalWorker;
    @Mock
    private AcmeSchedulingProperties acmeConfig;
    @Mock
    private TaskScheduler injectedTaskScheduler;

    @Test
    void withDedicatedSchedulerShutsDownItsOwnSchedulerOnDestroy() throws Exception {
        var scheduler = CertificateRenewalScheduler.withDedicatedScheduler(acmeRenewalWorker, acmeConfig, "test-owned-");
        var dedicatedScheduler = dedicatedScheduler(scheduler);
        assertThat(dedicatedScheduler.getScheduledExecutor().isShutdown()).isFalse();

        scheduler.destroy();

        assertThat(dedicatedScheduler.getScheduledExecutor().isShutdown()).isTrue();
    }

    @Test
    void rescheduleAfterDestroyIsANoOp() {
        var scheduler = CertificateRenewalScheduler.withDedicatedScheduler(acmeRenewalWorker, acmeConfig, "test-shutdown-race-");
        scheduler.destroy();

        assertThatCode(scheduler::init).doesNotThrowAnyException();
    }

    @Test
    void publicConstructorNeverTouchesAnInjectedScheduler() {
        var scheduler = new CertificateRenewalScheduler(acmeRenewalWorker, acmeConfig, injectedTaskScheduler);

        scheduler.destroy();

        verifyNoInteractions(injectedTaskScheduler);
    }

    @Test
    void deferCycleKeepsRetryBackoffSchedule() {
        var scheduler = schedulerWithInjectedClock();
        scheduler.failure();

        scheduler.deferCycle();
        runLastScheduledJob();

        assertThat(lastScheduledDelay()).isEqualTo(Duration.ofSeconds(RETRY_DELAY));
    }

    @Test
    void globalConfInvalidatedClearsRetryBackoffSchedule() {
        var scheduler = schedulerWithInjectedClock();
        scheduler.failure();

        scheduler.globalConfInvalidated();
        runLastScheduledJob();

        assertThat(lastScheduledDelay()).isEqualTo(Duration.ofSeconds(RENEWAL_INTERVAL));
    }

    private CertificateRenewalScheduler schedulerWithInjectedClock() {
        when(acmeConfig.getAcmeRenewalRetryDelay()).thenReturn(RETRY_DELAY);
        when(acmeConfig.getAcmeRenewalInterval()).thenReturn(RENEWAL_INTERVAL);
        when(injectedTaskScheduler.getClock()).thenReturn(Clock.fixed(NOW, ZoneOffset.UTC));
        return new CertificateRenewalScheduler(acmeRenewalWorker, acmeConfig, injectedTaskScheduler);
    }

    private void runLastScheduledJob() {
        var job = ArgumentCaptor.forClass(Runnable.class);
        verify(injectedTaskScheduler, atLeastOnce()).schedule(job.capture(), any(Instant.class));
        job.getValue().run();
    }

    private Duration lastScheduledDelay() {
        var startTime = ArgumentCaptor.forClass(Instant.class);
        verify(injectedTaskScheduler, atLeastOnce()).schedule(any(Runnable.class), startTime.capture());
        return Duration.between(NOW, startTime.getValue());
    }

    private static ThreadPoolTaskScheduler dedicatedScheduler(CertificateRenewalScheduler scheduler) throws Exception {
        Field field = CertificateRenewalScheduler.class.getDeclaredField("taskScheduler");
        field.setAccessible(true);
        return (ThreadPoolTaskScheduler) field.get(scheduler);
    }
}
