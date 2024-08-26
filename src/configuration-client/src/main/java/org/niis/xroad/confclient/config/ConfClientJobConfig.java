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
package org.niis.xroad.confclient.config;

import ee.ria.xroad.common.DiagnosticsErrorCodes;
import ee.ria.xroad.common.DiagnosticsStatus;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.ConfigurationClientJob;
import ee.ria.xroad.common.util.JobManager;
import ee.ria.xroad.common.util.TimeUtils;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.schedule.backup.ProxyConfigurationBackupJob;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.quartz.SchedulerException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

@Configuration
public class ConfClientJobConfig {

    @Bean(initMethod = "start", destroyMethod = "stop")
    JobManager jobManager(ConfigurationClientJobListener listener,
                          SpringBeanJobFactory springBeanJobFactory) throws SchedulerException {
        final var jobManager = new JobManager(springBeanJobFactory);

        jobManager.getJobScheduler().getListenerManager().addJobListener(listener);

        jobManager.registerRepeatingJob(ConfigurationClientJob.class,
                SystemProperties.getConfigurationClientUpdateIntervalSeconds(), new JobDataMap());

        jobManager.registerJob(ProxyConfigurationBackupJob.class,
                SystemProperties.getConfigurationClientProxyConfigurationBackupCron(), new JobDataMap());

        return jobManager;
    }

    @Bean
    ConfigurationClientJobListener getConfigurationClientJobListener() {
        return new ConfigurationClientJobListener();
    }

    @Bean
    public SpringBeanJobFactory springBeanJobFactory() {
        return new SpringBeanJobFactory();
    }

    /**
     * Listens for daemon job completions and collects results.
     */
    @Slf4j
    public static final class ConfigurationClientJobListener implements JobListener {
        public static final String LISTENER_NAME = "confClientJobListener";

        // Access only via synchronized getter/setter.
        private DiagnosticsStatus status;

        private synchronized void setStatus(DiagnosticsStatus newStatus) {
            status = newStatus;
        }

        public synchronized DiagnosticsStatus getStatus() {
            return status;
        }

        ConfigurationClientJobListener() {
            status = new DiagnosticsStatus(DiagnosticsErrorCodes.ERROR_CODE_UNINITIALIZED, TimeUtils.offsetDateTimeNow(),
                    TimeUtils.offsetDateTimeNow().plusSeconds(SystemProperties.getConfigurationClientUpdateIntervalSeconds()));
        }

        @Override
        public String getName() {
            return LISTENER_NAME;
        }

        @Override
        public void jobToBeExecuted(JobExecutionContext context) {
            // NOP
        }

        @Override
        public void jobExecutionVetoed(JobExecutionContext context) {
            // NOP
        }

        @Override
        public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
            if (context.getResult() instanceof DiagnosticsStatus diagnosticsStatus) {
                log.info("job was executed result={}", diagnosticsStatus);

                setStatus(diagnosticsStatus);
            }
        }
    }
}
