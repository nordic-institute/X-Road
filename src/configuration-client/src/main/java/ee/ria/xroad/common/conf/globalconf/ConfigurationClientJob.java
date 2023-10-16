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
package ee.ria.xroad.common.conf.globalconf;

import ee.ria.xroad.common.DiagnosticsErrorCodes;
import ee.ria.xroad.common.DiagnosticsStatus;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.JobManager;
import ee.ria.xroad.common.util.TimeUtils;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.schedule.RetryingQuartzJob;
import org.niis.xroad.schedule.backup.ProxyConfigurationBackupJob;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;

/**
 * Quartz job implementation for the configuration client.
 */
@Slf4j
@DisallowConcurrentExecution
public class ConfigurationClientJob extends RetryingQuartzJob {
    private static final int RETRY_DELAY_SEC = 3;

    public ConfigurationClientJob() {
        super(RETRY_DELAY_SEC);
    }

    @Override
    protected void executeWithRetry(JobExecutionContext context) throws Exception {
        JobDataMap data = context.getJobDetail().getJobDataMap();
        Object client = data.get("client");

        if (client instanceof ConfigurationClient) {
            try {
                ((ConfigurationClient) client).execute();

                DiagnosticsStatus status =
                        new DiagnosticsStatus(DiagnosticsErrorCodes.RETURN_SUCCESS, TimeUtils.offsetDateTimeNow(),
                                TimeUtils.offsetDateTimeNow()
                                        .plusSeconds(SystemProperties.getConfigurationClientUpdateIntervalSeconds()));
                context.setResult(status);
            } catch (Exception e) {
                DiagnosticsStatus status = new DiagnosticsStatus(ConfigurationClientUtils.getErrorCode(e),
                        TimeUtils.offsetDateTimeNow(),
                        TimeUtils.offsetDateTimeNow()
                                .plusSeconds(SystemProperties.getConfigurationClientUpdateIntervalSeconds()));
                context.setResult(status);

                throw new JobExecutionException(e);
            }
        } else {
            throw new JobExecutionException("Could not get configuration client from job data");
        }
    }

    @Override
    protected boolean shouldRescheduleRetry(JobExecutionContext context) throws SchedulerException {
        return JobManager.isJobRunning(context, ProxyConfigurationBackupJob.class);
    }

}
