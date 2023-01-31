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
import ee.ria.xroad.common.util.JobManager;
import ee.ria.xroad.common.util.process.ExternalProcessRunner;
import ee.ria.xroad.common.util.process.ProcessFailedException;
import ee.ria.xroad.common.util.process.ProcessNotExecutableException;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.schedule.RetryingQuartzJob;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.SchedulerException;

/**
 * A quartz job that executes proxy autobackup script.
 */
@Slf4j
@DisallowConcurrentExecution
public class ProxyConfigurationBackupJob extends RetryingQuartzJob {
    private static final String AUTOBACKUP_SCRIPT_PATH = "/usr/share/xroad/scripts/autobackup_xroad_proxy_configuration.sh";
    private static final int RETRY_DELAY_SEC = 5;

    private final ExternalProcessRunner externalProcessRunner;

    public ProxyConfigurationBackupJob() {
        super(RETRY_DELAY_SEC);
        this.externalProcessRunner = new ExternalProcessRunner();
    }

    ProxyConfigurationBackupJob(ExternalProcessRunner externalProcessRunner) {
        super(RETRY_DELAY_SEC);
        this.externalProcessRunner = externalProcessRunner;
    }

    @Override
    protected void executeWithRetry(JobExecutionContext context)
            throws ProcessFailedException, InterruptedException, ProcessNotExecutableException {
        log.info("Executing security server configuration auto-backup...");
        ExternalProcessRunner.ProcessResult processResult = externalProcessRunner.executeAndThrowOnFailure(AUTOBACKUP_SCRIPT_PATH);
        log.info("Auto-backup execution output: {}", String.join("\n", processResult.getProcessOutput()));
    }

    @Override
    protected boolean shouldRescheduleRetry(JobExecutionContext context) throws SchedulerException {
        return JobManager.isJobRunning(context, ConfigurationClientJob.class);
    }

}
