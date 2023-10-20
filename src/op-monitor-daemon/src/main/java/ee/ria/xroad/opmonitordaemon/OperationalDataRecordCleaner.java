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
package ee.ria.xroad.opmonitordaemon;

import ee.ria.xroad.common.opmonitoring.OpMonitoringSystemProperties;
import ee.ria.xroad.common.util.JobManager;
import ee.ria.xroad.common.util.TimeUtils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.SchedulerException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import static ee.ria.xroad.opmonitordaemon.OpMonitorDaemonDatabaseCtx.doInTransaction;

/**
 * Deletes outdated operational data records from the database.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class OperationalDataRecordCleaner {

    /**
     * Initializes the operational data recorder cleaner creating an operational
     * data records cleaner job and scheduling a
     * periodic cleanup with the provided job manager.
     *
     * @param jobManager the job manager
     */
    public static void init(JobManager jobManager) {
        registerCronJob(jobManager, OpMonitoringSystemProperties.getOpMonitorCleanInterval());
    }

    public static void doClean() {
        try {
            handleCleanup();
        } catch (Exception e) {
            log.error("Failed to clean outdated operational data records"
                    + " from the database", e);
        }
    }

    private static void handleCleanup() throws Exception {
        cleanRecords(
                TimeUtils.now().minus(OpMonitoringSystemProperties.getOpMonitorKeepRecordsForDays(), ChronoUnit.DAYS));
    }

    static int cleanRecords(Instant before) throws Exception {
        log.trace("cleanRecords({})", before);

        return doInTransaction(session -> {
            String hql =
                    "delete OperationalDataRecord r where r.monitoringDataTs < "
                            + TimeUnit.MILLISECONDS.toSeconds(before.toEpochMilli());

            int removed = session.createQuery(hql).executeUpdate();

            if (removed == 0) {
                log.info("No outdated operational data records to remove from"
                        + " the database");
            } else {
                log.info("Removed {} outdated operational data records from"
                        + " the database", removed);
            }

            return removed;
        });
    }

    private static void registerCronJob(JobManager jobManager, String cronExpression) {

        try {
            jobManager.registerJob(OperationalDataRecordCleanerJob.class,
                    OperationalDataRecordCleanerJob.class.getSimpleName(), cronExpression,
                    new JobDataMap());
        } catch (SchedulerException e) {
            log.error("Unable to schedule job", e);
        }
    }

    @DisallowConcurrentExecution
    public static class OperationalDataRecordCleanerJob implements Job {

        @Override
        public void execute(JobExecutionContext context) {
            OperationalDataRecordCleaner.doClean();
        }
    }
}
