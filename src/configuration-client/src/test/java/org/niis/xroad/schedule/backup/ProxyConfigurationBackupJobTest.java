/**
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.schedule.backup;

import ee.ria.xroad.common.conf.globalconf.ConfigurationClientJob;
import ee.ria.xroad.common.util.process.ExternalProcessRunner;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;

import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ProxyConfigurationBackupJobTest {
    @Mock
    private ExternalProcessRunner externalProcessRunner;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private JobExecutionContext jobExecutionContext;

    private ProxyConfigurationBackupJob configurationBackupJob;

    @Before
    public void setUp() {
        configurationBackupJob = new ProxyConfigurationBackupJob(externalProcessRunner);
    }

    @Test
    public void shouldTriggerBashScript() throws Exception {
        when(jobExecutionContext.getScheduler().getCurrentlyExecutingJobs()).thenReturn(new ArrayList<>());
        when(externalProcessRunner.executeAndThrowOnFailure(anyString()))
                .thenReturn(new ExternalProcessRunner.ProcessResult("", 0, new ArrayList<>()));

        configurationBackupJob.execute(jobExecutionContext);

        verify(externalProcessRunner, times(1)).executeAndThrowOnFailure(anyString());
    }

    @Test
    public void shouldRetryIfConditionMet() throws Exception {
        when(jobExecutionContext.getJobDetail().getKey())
                .thenReturn(new JobKey(ProxyConfigurationBackupJob.class.getSimpleName()));

        JobExecutionContext runningContext = mock(JobExecutionContext.class, Answers.RETURNS_DEEP_STUBS);
        when(runningContext.getJobDetail().getJobClass()).thenAnswer(invocation -> ConfigurationClientJob.class);

        when(jobExecutionContext.getScheduler().getCurrentlyExecutingJobs()).thenReturn(Lists.newArrayList(runningContext));

        configurationBackupJob.execute(jobExecutionContext);

        verify(externalProcessRunner, never()).executeAndThrowOnFailure(anyString());
    }
}
