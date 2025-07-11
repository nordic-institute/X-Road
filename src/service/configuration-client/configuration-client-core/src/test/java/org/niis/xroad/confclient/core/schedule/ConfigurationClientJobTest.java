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
package org.niis.xroad.confclient.core.schedule;

import ee.ria.xroad.common.DiagnosticsErrorCodes;
import ee.ria.xroad.common.util.JobManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.niis.xroad.confclient.core.ConfigurationClient;
import org.niis.xroad.confclient.core.ConfigurationClientJob;
import org.niis.xroad.confclient.core.ConfigurationClientUtils;
import org.niis.xroad.confclient.core.schedule.backup.ProxyConfigurationBackupJob;
import org.niis.xroad.globalconf.status.DiagnosticsStatus;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.confclient.core.ConfigurationClientJob.PROXY_CONFIGURATION_BACKUP_JOB;

class ConfigurationClientJobTest {

    private static final int ERROR_CODE = 122;
    private ConfigurationClient configClient;
    private JobExecutionContext jobExecutionContext;
    private ConfigurationClientJob job;
    private MockedStatic<JobManager> jobManagerMock;

    @BeforeEach
    void setUp() {
        configClient = mock(ConfigurationClient.class);
        jobExecutionContext = mock(JobExecutionContext.class);
        job = new ConfigurationClientJob(configClient);

        jobManagerMock = mockStatic(JobManager.class);
    }

    @AfterEach
    void tearDown() {
        jobManagerMock.close();
    }

    @Test
    void executeSuccess() throws Exception {
        when(JobManager.isJobRunning(jobExecutionContext, PROXY_CONFIGURATION_BACKUP_JOB)).thenReturn(false);

        job.execute(jobExecutionContext);

        verify(configClient).execute();
        verify(jobExecutionContext).setResult(any(DiagnosticsStatus.class));

        ArgumentCaptor<DiagnosticsStatus> captor = ArgumentCaptor.forClass(DiagnosticsStatus.class);
        verify(jobExecutionContext).setResult(captor.capture());
        DiagnosticsStatus result = captor.getValue();

        assertEquals(DiagnosticsErrorCodes.RETURN_SUCCESS, result.getReturnCode());
        assertNotNull(result.getPrevUpdate());
        assertTrue(result.getNextUpdate().isAfter(result.getPrevUpdate()));
    }

    @Test
    void executeWithBackupJobRunning() throws Exception {
        when(JobManager.isJobRunning(jobExecutionContext, PROXY_CONFIGURATION_BACKUP_JOB)).thenReturn(true);

        job.execute(jobExecutionContext);

        verify(configClient, never()).execute();
        verify(jobExecutionContext, never()).setResult(any());
    }

    @Test
    void executeThrownException() throws Exception {
        mockStatic(ConfigurationClientUtils.class);
        when(JobManager.isJobRunning(jobExecutionContext, PROXY_CONFIGURATION_BACKUP_JOB)).thenReturn(false);
        doThrow(new RuntimeException("Simulated error")).when(configClient).execute();
        when(ConfigurationClientUtils.getErrorCode(any())).thenReturn(ERROR_CODE);

        JobExecutionException exception = assertThrows(JobExecutionException.class, () -> {
            job.execute(jobExecutionContext);
        });

        assertEquals("Simulated error", exception.getCause().getMessage());

        ArgumentCaptor<DiagnosticsStatus> captor = ArgumentCaptor.forClass(DiagnosticsStatus.class);
        verify(jobExecutionContext).setResult(captor.capture());
        DiagnosticsStatus status = captor.getValue();

        assertEquals(ERROR_CODE, status.getReturnCode());
        assertNotNull(status.getPrevUpdate());
        assertTrue(status.getNextUpdate().isAfter(status.getPrevUpdate()));
    }

    @Test
    void jobNameShouldMatchActualClassName() {
        assertEquals(PROXY_CONFIGURATION_BACKUP_JOB, ProxyConfigurationBackupJob.class.getSimpleName());
    }
}
