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

package org.niis.xroad.auxiliaryservice.core.messagelog;

import ee.ria.xroad.common.util.process.BlockingProcessRunner;

import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.ScheduledExecution;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.scheduler.common.runtime.util.SchedulerUtils;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.auxiliaryservice.core.config.MessageLogJobsProperties;
import org.niis.xroad.auxiliaryservice.core.config.ProxyMessageLogProperties;

@Startup
@ApplicationScoped
@Slf4j
@RequiredArgsConstructor
public class MessageLogCleanupJob {

    private final Scheduler scheduler;
    private final MessageLogJobsProperties properties;
    private final ProxyMessageLogProperties messageLogProperties;
    private final BlockingProcessRunner blockingProcessRunner;
    private final Scheduled.ApplicationNotRunning applicationNotRunning;

    @PostConstruct
    public void init() {
        if (isEnabled()) {
            log.info("Scheduling message log cleanup with cron expression: '{}'", properties.cleanupCron());
            scheduler.newJob(getClass().getSimpleName())
                    .setCron(properties.cleanupCron())
                    .setTask(this::execute)
                    .setConcurrentExecution(Scheduled.ConcurrentExecution.SKIP)
                    .setSkipPredicate(applicationNotRunning)
                    .schedule();
        } else {
            log.info("Message log cleanup job is disabled.");
        }
    }

    void executeOnShutdown() {
        if (isEnabled()) {
            execute();
        } else {
            log.info("Message log cleanup is disabled, skipping cleanup on shutdown.");
        }
    }

    private boolean isEnabled() {
        return messageLogProperties.isEnabled()
                && StringUtils.isNotBlank(properties.cleanupCron())
                && !SchedulerUtils.isOff(properties.cleanupCron());
    }

    private void execute(ScheduledExecution execution) {
        execute();
    }

    private void execute() {
        try {
            log.info("Executing message log cleanup");
            var result = blockingProcessRunner
                    .executeAndThrowOnFailure(properties.commandPath(), "cleanup");
            log.info("Message log cleanup job completed: {}", String.join("\n", result.getProcessOutput()));
        } catch (Exception e) {
            log.error("Error executing message log cleanup job.", e);
        }
    }

}
