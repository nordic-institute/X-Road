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

import ee.ria.xroad.common.db.DatabaseCtxV2;
import ee.ria.xroad.common.opmonitoring.OpMonitoringSystemProperties;
import ee.ria.xroad.common.util.TimeUtils;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

/**
 * Deletes outdated operational data records from the database.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public final class OperationalDataRecordCleanerJob {

    private final DatabaseCtxV2 databaseCtx;

    @Scheduled(cron = "${xroad.op-monitor.clean-interval}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void doClean() {
        try {
            handleCleanup(databaseCtx);
        } catch (Exception e) {
            log.error("Failed to clean outdated operational data records"
                    + " from the database", e);
        }
    }

    private void handleCleanup(DatabaseCtxV2 opMonitorDatabaseCtx) throws Exception {
        cleanRecords(
                TimeUtils.now().minus(OpMonitoringSystemProperties.getOpMonitorKeepRecordsForDays(), ChronoUnit.DAYS),
                opMonitorDatabaseCtx);
    }

    int cleanRecords(Instant before, DatabaseCtxV2 opMonitorDatabaseCtx) throws Exception {
        log.trace("cleanRecords({})", before);

        return opMonitorDatabaseCtx.doInTransaction(session -> {
            String hql =
                    "delete OperationalDataRecordEntity r where r.monitoringDataTs < "
                            + TimeUnit.MILLISECONDS.toSeconds(before.toEpochMilli());

            int removed = session.createMutationQuery(hql).executeUpdate();

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

}
