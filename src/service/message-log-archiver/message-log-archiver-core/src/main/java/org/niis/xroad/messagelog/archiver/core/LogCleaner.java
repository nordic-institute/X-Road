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
package org.niis.xroad.messagelog.archiver.core;

import ee.ria.xroad.common.db.DatabaseCtx;
import ee.ria.xroad.common.util.TimeUtils;
import ee.ria.xroad.messagelog.database.MessageLogDatabaseConfig;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.query.MutationQuery;

import java.time.temporal.ChronoUnit;

/**
 * Deletes all archived log records from the database.
 */
@Slf4j
@ApplicationScoped
public class LogCleaner {
    private final LogArchiverProperties logArchiverProperties;
    private final DatabaseCtx databaseCtx;

    public LogCleaner(LogArchiverProperties logArchiverProperties,
                      @Named(MessageLogDatabaseConfig.MESSAGE_LOG_DB_CTX) DatabaseCtx databaseCtx) {
        this.logArchiverProperties = logArchiverProperties;
        this.databaseCtx = databaseCtx;
    }

    public void execute() {
        try {
            log.info("Removing archived records from database...");
            final long removed = handleClean();
            if (removed == 0) {
                log.info("No archived records to remove from database");
            } else {
                log.info("Removed {} archived records from database", removed);
            }
        } catch (Exception e) {
            log.error("Error when cleaning archived records from database", e);
        }
    }

    protected long handleClean() {

        final Long time =
                TimeUtils.now().minus(logArchiverProperties.cleanKeepRecordsFor(), ChronoUnit.DAYS).toEpochMilli();
        long count = 0;
        int removed;
        do {
            removed = databaseCtx.doInTransaction(session -> {
                final MutationQuery query = session.createNamedMutationQuery("delete-logrecords");
                query.setParameter("time", time);
                query.setParameter("limit", logArchiverProperties.cleanTransactionBatchSize());
                return query.executeUpdate();
            });
            log.debug("Removed {} archived records", removed);
            count += removed;
        } while (removed > 0);
        return count;
    }
}
