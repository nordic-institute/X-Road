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
package org.niis.xroad.confclient.core;

import ee.ria.xroad.common.DiagnosticsErrorCodes;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.JobManager;
import ee.ria.xroad.common.util.TimeUtils;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.confclient.core.schedule.backup.ProxyConfigurationBackupJob;
import org.niis.xroad.globalconf.status.DiagnosticsStatus;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * Quartz job implementation for the configuration client.
 */
@Slf4j
@DisallowConcurrentExecution
public class ConfigurationClientJob implements Job {

    private final ConfigurationClient configClient;

    public ConfigurationClientJob(ConfigurationClient configClient) {
        this.configClient = configClient;
    }

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        try {
            if (JobManager.isJobRunning(jobExecutionContext, ProxyConfigurationBackupJob.class)) {
                log.warn("Proxy configuration backup job is running, skipping configuration client execution.");
                return;
            }
            configClient.execute();

            DiagnosticsStatus status =
                    new DiagnosticsStatus(DiagnosticsErrorCodes.RETURN_SUCCESS, TimeUtils.offsetDateTimeNow(),
                            TimeUtils.offsetDateTimeNow()
                                    .plusSeconds(SystemProperties.getConfigurationClientUpdateIntervalSeconds()));

            jobExecutionContext.setResult(status);
        } catch (Exception e) {
            DiagnosticsStatus status = new DiagnosticsStatus(ConfigurationClientUtils.getErrorCode(e),
                    TimeUtils.offsetDateTimeNow(),
                    TimeUtils.offsetDateTimeNow()
                            .plusSeconds(SystemProperties.getConfigurationClientUpdateIntervalSeconds()));
            jobExecutionContext.setResult(status);

            log.error("Error executing job.", e);
            throw new JobExecutionException(e);
        }
    }
}
