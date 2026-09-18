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
package org.niis.xroad.cs.admin.core.dataspace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.cs.admin.api.service.DataspaceIssuerProvisioningService;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.cs.admin.core.dataspace.DataspaceIssuerProvisioningWorker.Status;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataspaceIssuerProvisioningWorkerTest {

    private static final String INSTANCE_IDENTIFIER = "TEST";
    private static final String CENTRAL_SERVER_ADDRESS = "cs.example";
    private static final Instant NOW = Instant.parse("2024-01-01T00:00:00Z");

    @Mock
    private DataspaceIssuerProvisioningService dataspaceIssuerProvisioningService;
    @Mock
    private SystemParameterService systemParameterService;
    @Mock
    private DataspaceIssuerProperties dataspaceIssuerProperties;
    @Mock
    private TaskScheduler taskScheduler;

    private DataspaceIssuerProvisioningWorker worker;

    @BeforeEach
    void setUp() {
        lenient().when(taskScheduler.getClock()).thenReturn(Clock.fixed(NOW, ZoneOffset.UTC));

        worker = new DataspaceIssuerProvisioningWorker(dataspaceIssuerProvisioningService, systemParameterService,
                dataspaceIssuerProperties, taskScheduler);
    }

    @Test
    void startsInWaitingForConfigurationState() {
        assertThat(worker.getState().status()).isEqualTo(Status.WAITING_FOR_CONFIGURATION);
        assertThat(worker.getState().lastError()).isNull();
        assertThat(worker.getState().lastAttemptAt()).isNull();
    }

    @Test
    void skipsWhileNotInitialized() {
        when(systemParameterService.getInstanceIdentifier()).thenReturn("");

        ReflectionTestUtils.invokeMethod(worker, "scheduledProvision");

        verify(dataspaceIssuerProvisioningService, never()).provisionIssuer();
        assertThat(worker.getState().status()).isEqualTo(Status.WAITING_FOR_CONFIGURATION);
        verify(taskScheduler).schedule(any(Runnable.class),
                eq(NOW.plus(Duration.ofSeconds(30))));
    }

    @Test
    void skipsWhileCentralServerAddressNotSet() {
        when(systemParameterService.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);
        when(systemParameterService.getCentralServerAddress()).thenReturn("");

        ReflectionTestUtils.invokeMethod(worker, "scheduledProvision");

        verify(dataspaceIssuerProvisioningService, never()).provisionIssuer();
        assertThat(worker.getState().status()).isEqualTo(Status.WAITING_FOR_CONFIGURATION);
    }

    @Test
    void skipsWhileIssuerHostNotConfigured() {
        when(systemParameterService.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);
        when(systemParameterService.getCentralServerAddress()).thenReturn(CENTRAL_SERVER_ADDRESS);
        when(dataspaceIssuerProperties.isHostConfigured()).thenReturn(false);

        ReflectionTestUtils.invokeMethod(worker, "scheduledProvision");

        verify(dataspaceIssuerProvisioningService, never()).provisionIssuer();
        assertThat(worker.getState().status()).isEqualTo(Status.WAITING_FOR_CONFIGURATION);
    }

    @Test
    void startsPromptlyOnceBothPreconditionsAreMet() {
        when(systemParameterService.getInstanceIdentifier()).thenReturn("", INSTANCE_IDENTIFIER);
        when(systemParameterService.getCentralServerAddress()).thenReturn(CENTRAL_SERVER_ADDRESS);
        when(dataspaceIssuerProperties.isHostConfigured()).thenReturn(true);

        ReflectionTestUtils.invokeMethod(worker, "scheduledProvision");
        ReflectionTestUtils.invokeMethod(worker, "scheduledProvision");

        verify(dataspaceIssuerProvisioningService, times(1)).provisionIssuer();
        assertThat(worker.getState().status()).isEqualTo(Status.PROVISIONED);
    }

    @Test
    void provisionsOnceAndStops() {
        when(systemParameterService.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);
        when(systemParameterService.getCentralServerAddress()).thenReturn(CENTRAL_SERVER_ADDRESS);
        when(dataspaceIssuerProperties.isHostConfigured()).thenReturn(true);

        ReflectionTestUtils.invokeMethod(worker, "scheduledProvision");
        ReflectionTestUtils.invokeMethod(worker, "scheduledProvision");

        verify(dataspaceIssuerProvisioningService, times(1)).provisionIssuer();
        assertThat(worker.getState().status()).isEqualTo(Status.PROVISIONED);
    }

    @Test
    void retriesAfterFailureAndRecoversOnceIssuerBecomesReachable() {
        when(systemParameterService.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);
        when(systemParameterService.getCentralServerAddress()).thenReturn(CENTRAL_SERVER_ADDRESS);
        when(dataspaceIssuerProperties.isHostConfigured()).thenReturn(true);
        doThrow(new RuntimeException("issuer unreachable"))
                .doNothing()
                .when(dataspaceIssuerProvisioningService).provisionIssuer();

        assertThatCode(() -> ReflectionTestUtils.invokeMethod(worker, "scheduledProvision")).doesNotThrowAnyException();
        assertThat(worker.getState().status()).isEqualTo(Status.FAILING);
        assertThat(worker.getState().lastError()).isNotNull();
        assertThat(worker.getState().lastAttemptAt()).isNotNull();
        verify(taskScheduler).schedule(any(Runnable.class), eq(NOW.plus(Duration.ofSeconds(30))));

        ReflectionTestUtils.invokeMethod(worker, "scheduledProvision");

        verify(dataspaceIssuerProvisioningService, times(2)).provisionIssuer();
        assertThat(worker.getState().status()).isEqualTo(Status.PROVISIONED);
    }

    @Test
    void backsOffExponentiallyUpToCapOnRepeatedFailures() {
        when(systemParameterService.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);
        when(systemParameterService.getCentralServerAddress()).thenReturn(CENTRAL_SERVER_ADDRESS);
        when(dataspaceIssuerProperties.isHostConfigured()).thenReturn(true);
        doThrow(new RuntimeException("issuer unreachable")).when(dataspaceIssuerProvisioningService).provisionIssuer();

        ReflectionTestUtils.invokeMethod(worker, "scheduledProvision");
        verify(taskScheduler).schedule(any(Runnable.class), eq(NOW.plus(Duration.ofSeconds(30))));

        ReflectionTestUtils.invokeMethod(worker, "scheduledProvision");
        verify(taskScheduler).schedule(any(Runnable.class), eq(NOW.plus(Duration.ofSeconds(60))));

        ReflectionTestUtils.invokeMethod(worker, "scheduledProvision");
        verify(taskScheduler).schedule(any(Runnable.class), eq(NOW.plus(Duration.ofSeconds(120))));

        ReflectionTestUtils.invokeMethod(worker, "scheduledProvision");
        verify(taskScheduler).schedule(any(Runnable.class), eq(NOW.plus(Duration.ofSeconds(240))));

        ReflectionTestUtils.invokeMethod(worker, "scheduledProvision");
        verify(taskScheduler).schedule(any(Runnable.class), eq(NOW.plus(Duration.ofMinutes(5))));

        ReflectionTestUtils.invokeMethod(worker, "scheduledProvision");
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), eq(NOW.plus(Duration.ofMinutes(5))));

        assertThat(worker.getState().status()).isEqualTo(Status.FAILING);
        verify(dataspaceIssuerProvisioningService, times(6)).provisionIssuer();
    }

    @Test
    void returnsToWaitingForConfigurationAfterAFailureAndResetsBackoff() {
        when(systemParameterService.getCentralServerAddress()).thenReturn(CENTRAL_SERVER_ADDRESS);
        when(dataspaceIssuerProperties.isHostConfigured()).thenReturn(true);
        doThrow(new RuntimeException("issuer unreachable")).when(dataspaceIssuerProvisioningService).provisionIssuer();

        when(systemParameterService.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);
        ReflectionTestUtils.invokeMethod(worker, "scheduledProvision");

        assertThat(worker.getState().status()).isEqualTo(Status.FAILING);
        verify(taskScheduler).schedule(any(Runnable.class), eq(NOW.plus(Duration.ofSeconds(30))));

        when(systemParameterService.getInstanceIdentifier()).thenReturn("");
        ReflectionTestUtils.invokeMethod(worker, "scheduledProvision");

        assertThat(worker.getState().status()).isEqualTo(Status.WAITING_FOR_CONFIGURATION);
        assertThat(worker.getState().lastError()).isNull();
        assertThat(worker.getState().lastAttemptAt()).isNull();
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), eq(NOW.plus(Duration.ofSeconds(30))));

        when(systemParameterService.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);
        ReflectionTestUtils.invokeMethod(worker, "scheduledProvision");

        assertThat(worker.getState().status()).isEqualTo(Status.FAILING);
        verify(taskScheduler, times(3)).schedule(any(Runnable.class), eq(NOW.plus(Duration.ofSeconds(30))));
        verify(taskScheduler, never()).schedule(any(Runnable.class), eq(NOW.plus(Duration.ofSeconds(60))));
    }

    @Test
    void treatsAnUnexpectedPreconditionCheckFailureAsFailingAndBacksOff() {
        when(systemParameterService.getInstanceIdentifier()).thenThrow(new RuntimeException("db unavailable"));

        assertThatCode(() -> ReflectionTestUtils.invokeMethod(worker, "scheduledProvision")).doesNotThrowAnyException();

        assertThat(worker.getState().status()).isEqualTo(Status.FAILING);
        assertThat(worker.getState().lastError()).isNotNull();
        assertThat(worker.getState().lastAttemptAt()).isNotNull();
        verify(dataspaceIssuerProvisioningService, never()).provisionIssuer();
        verify(taskScheduler).schedule(any(Runnable.class), eq(NOW.plus(Duration.ofSeconds(30))));
    }

    @Test
    void stopsReschedulingAfterDestroy() {
        worker.destroy();

        ReflectionTestUtils.invokeMethod(worker, "scheduledProvision");

        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }
}
