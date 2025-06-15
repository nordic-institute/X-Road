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

package org.niis.xroad.backupmanager.core.job;

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
import org.niis.xroad.backupmanager.core.BackupManagerProperties;
import org.niis.xroad.backupmanager.core.FileSystemBackupHandler;

@Startup
@ApplicationScoped
@Slf4j
@RequiredArgsConstructor
public class AutoBackupJob {

    private final Scheduler scheduler;
    private final BackupManagerProperties backupManagerProperties;
    private final FileSystemBackupHandler fileSystemBackupHandler;

    @PostConstruct
    public void init() {
        if (StringUtils.isNoneBlank(backupManagerProperties.autoBackupCronExpression(),
                backupManagerProperties.autoBackupScriptPath())
                && !SchedulerUtils.isOff(backupManagerProperties.autoBackupCronExpression())) {
            log.info("Scheduling automatic backups with cron expression: '{}'",
                    backupManagerProperties.autoBackupCronExpression());
            scheduler.newJob(getClass().getSimpleName())
                    .setCron(backupManagerProperties.autoBackupCronExpression())
                    .setTask(this::execute)
                    .setConcurrentExecution(Scheduled.ConcurrentExecution.SKIP)
                    .schedule();
        } else {
            log.info("Automatic backups are disabled. No cron expression or script path provided.");
        }
    }

    private void execute(ScheduledExecution execution) {
        fileSystemBackupHandler.createAutomaticBackup();
    }

}
