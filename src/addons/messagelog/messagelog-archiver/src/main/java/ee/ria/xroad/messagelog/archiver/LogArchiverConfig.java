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
package ee.ria.xroad.messagelog.archiver;

import ee.ria.xroad.common.conf.globalconf.GlobalConfBeanConfig;
import ee.ria.xroad.common.conf.globalconf.GlobalConfRefreshJobConfig;
import ee.ria.xroad.common.messagelog.MessageLogProperties;
import ee.ria.xroad.common.util.JobManager;
import ee.ria.xroad.common.util.SpringAwareJobManager;

import org.quartz.JobDataMap;
import org.quartz.SchedulerException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

@Import({GlobalConfBeanConfig.class,
        GlobalConfRefreshJobConfig.class})
@Configuration
public class LogArchiverConfig {

    @Bean(initMethod = "start", destroyMethod = "stop")
    JobManager jobManager(SpringBeanJobFactory springBeanJobFactory) throws SchedulerException {
        final var jobManager = new SpringAwareJobManager(springBeanJobFactory);

        jobManager.registerJob(LogArchiver.class, "ArchiverJob", MessageLogProperties.getArchiveInterval(),
                new JobDataMap());

        jobManager.registerJob(LogCleaner.class, "CleanerJob", MessageLogProperties.getCleanInterval(),
                new JobDataMap());

        return jobManager;
    }

    @Bean
    SpringBeanJobFactory springBeanJobFactory() {
        return new SpringBeanJobFactory();
    }
}
