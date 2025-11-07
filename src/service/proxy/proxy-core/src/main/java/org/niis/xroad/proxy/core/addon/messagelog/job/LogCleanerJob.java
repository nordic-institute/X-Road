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
package org.niis.xroad.proxy.core.addon.messagelog.job;

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
import org.niis.xroad.management.rpc.ManagementRpcClient;
import org.niis.xroad.messagelog.archiver.proto.MessageLogCleanupRequest;
import org.niis.xroad.messagelog.archiver.proto.MessageLogCleanupResp;
import org.niis.xroad.messagelog.archiver.proto.MessageLogConfig;
import org.niis.xroad.proxy.core.configuration.ProxyMessageLogProperties;

@Slf4j
@Startup
@ApplicationScoped
@RequiredArgsConstructor
public class LogCleanerJob {
    private final Scheduler scheduler;
    private final Scheduled.ApplicationNotRunning applicationNotRunning;
    private final ProxyMessageLogProperties messageLogProperties;
    private final ManagementRpcClient rpcClient;
    private final MessageLogConfigMapper configMapper;

    @PostConstruct
    public void init() {
        var archiverProps = messageLogProperties.archiver();

        if (archiverProps.enabled()
                && StringUtils.isNotBlank(archiverProps.cleanInterval())
                && !SchedulerUtils.isOff(archiverProps.cleanInterval())) {
            log.info("Scheduling {}", this.getClass().getSimpleName());
            scheduler.newJob(this.getClass().getSimpleName())
                    .setCron(archiverProps.cleanInterval())
                    .setTask(this::execute)
                    .setConcurrentExecution(Scheduled.ConcurrentExecution.SKIP)
                    .setSkipPredicate(applicationNotRunning)
                    .schedule();
        } else {
            log.info("{} is disabled.", this.getClass().getSimpleName());
        }
    }

    void execute(ScheduledExecution execution) {
        try {
            log.debug("Executing {}", this.getClass().getSimpleName());

            MessageLogConfig config = configMapper.buildMessageLogConfig();
            MessageLogCleanupRequest request = MessageLogCleanupRequest.newBuilder()
                    .setMessageLogConfig(config)
                    .build();

            MessageLogCleanupResp response = rpcClient.triggerCleanup(request);

            if (response.getSuccess()) {
                log.info("Log cleanup completed successfully: {} (records removed: {})",
                        response.getMessage(), response.getRecordsRemoved());
            } else {
                log.error("Log cleanup failed: {}", response.getMessage());
            }
        } catch (Exception e) {
            log.error("Error when triggering log cleanup via management rpc", e);
        }
    }
}
