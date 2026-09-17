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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.cs.admin.api.service.DataspaceIssuerProvisioningService;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.cs.admin.core.dataspace.DataspaceIssuerProvisioningWorker.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataspaceIssuerProvisioningWorkerTest {

    private static final String INSTANCE_IDENTIFIER = "TEST";
    private static final String CENTRAL_SERVER_ADDRESS = "cs.example";

    @Mock
    private DataspaceIssuerProvisioningService dataspaceIssuerProvisioningService;
    @Mock
    private SystemParameterService systemParameterService;
    @Mock
    private DataspaceIssuerProperties dataspaceIssuerProperties;

    @InjectMocks
    private DataspaceIssuerProvisioningWorker worker;

    @Test
    void startsInWaitingForConfigurationState() {
        assertThat(worker.getState().status()).isEqualTo(Status.WAITING_FOR_CONFIGURATION);
        assertThat(worker.getState().lastError()).isNull();
        assertThat(worker.getState().lastAttemptAt()).isNull();
    }

    @Test
    void skipsWhileNotInitialized() {
        when(systemParameterService.getInstanceIdentifier()).thenReturn("");

        worker.scheduledProvision();

        verify(dataspaceIssuerProvisioningService, never()).provisionIssuer();
        assertThat(worker.getState().status()).isEqualTo(Status.WAITING_FOR_CONFIGURATION);
    }

    @Test
    void skipsWhileCentralServerAddressNotSet() {
        when(systemParameterService.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);
        when(systemParameterService.getCentralServerAddress()).thenReturn("");

        worker.scheduledProvision();

        verify(dataspaceIssuerProvisioningService, never()).provisionIssuer();
        assertThat(worker.getState().status()).isEqualTo(Status.WAITING_FOR_CONFIGURATION);
    }

    @Test
    void skipsWhileIssuerHostNotConfigured() {
        when(systemParameterService.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);
        when(systemParameterService.getCentralServerAddress()).thenReturn(CENTRAL_SERVER_ADDRESS);
        when(dataspaceIssuerProperties.isHostConfigured()).thenReturn(false);

        worker.scheduledProvision();

        verify(dataspaceIssuerProvisioningService, never()).provisionIssuer();
        assertThat(worker.getState().status()).isEqualTo(Status.WAITING_FOR_CONFIGURATION);
    }

    @Test
    void startsPromptlyOnceBothPreconditionsAreMet() {
        when(systemParameterService.getInstanceIdentifier()).thenReturn("", INSTANCE_IDENTIFIER);
        when(systemParameterService.getCentralServerAddress()).thenReturn(CENTRAL_SERVER_ADDRESS);
        when(dataspaceIssuerProperties.isHostConfigured()).thenReturn(true);

        worker.scheduledProvision();
        worker.scheduledProvision();

        verify(dataspaceIssuerProvisioningService, times(1)).provisionIssuer();
        assertThat(worker.getState().status()).isEqualTo(Status.PROVISIONED);
    }

    @Test
    void provisionsOnceAndStops() {
        when(systemParameterService.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);
        when(systemParameterService.getCentralServerAddress()).thenReturn(CENTRAL_SERVER_ADDRESS);
        when(dataspaceIssuerProperties.isHostConfigured()).thenReturn(true);

        worker.scheduledProvision();
        worker.scheduledProvision();

        verify(dataspaceIssuerProvisioningService, times(1)).provisionIssuer();
        assertThat(worker.getState().status()).isEqualTo(Status.PROVISIONED);
    }

    @Test
    void retriesAfterFailure() {
        when(systemParameterService.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);
        when(systemParameterService.getCentralServerAddress()).thenReturn(CENTRAL_SERVER_ADDRESS);
        when(dataspaceIssuerProperties.isHostConfigured()).thenReturn(true);
        doThrow(new RuntimeException("issuer unreachable"))
                .doNothing()
                .when(dataspaceIssuerProvisioningService).provisionIssuer();

        assertThatCode(() -> worker.scheduledProvision()).doesNotThrowAnyException();
        assertThat(worker.getState().status()).isEqualTo(Status.FAILING);
        assertThat(worker.getState().lastError()).isNotNull();
        assertThat(worker.getState().lastAttemptAt()).isNotNull();

        worker.scheduledProvision();

        verify(dataspaceIssuerProvisioningService, times(2)).provisionIssuer();
        assertThat(worker.getState().status()).isEqualTo(Status.PROVISIONED);
    }
}
