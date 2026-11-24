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
package org.niis.xroad.messagelog;

import ee.ria.xroad.common.db.DatabaseCtx;

import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MessageLogDatabaseCtx extends DatabaseCtx {
    public static final String DB_NAME = "messagelog";

    private static final String PENDING_MESSAGES_QUERY = "from MessageRecordEntity m where m.timestampRecord is null";

    @Inject
    public MessageLogDatabaseCtx(Map<String, String> hibernateProperties) {
        super(DB_NAME, hibernateProperties);
    }

    /**
     * Finds the oldest pending messages, by id, up to the given limit.
     *
     * @param limit the maximum number of tasks to return
     * @return the oldest pending messages, oldest first
     */
    public List<TimestampTask> findTimestampTasks(int limit) {
        return doInTransaction(session -> session.createQuery(
                        "select new " + TimestampTask.class.getName() + "(m.id, m.signatureHash) "
                                + PENDING_MESSAGES_QUERY + " order by m.id", TimestampTask.class)
                .setMaxResults(limit)
                .list());
    }

    /**
     * Counts the pending messages.
     *
     * @return the number of pending messages
     */
    public long countTimestampTasks() {
        return doInTransaction(session -> session.createQuery(
                "select count(m) " + PENDING_MESSAGES_QUERY, Long.class).uniqueResult());
    }

    /**
     * Finds the log time of the oldest pending message, by id.
     *
     * @return the log time of the oldest pending message, or empty when none is pending
     */
    public Optional<Instant> findOldestTimestampTaskTime() {
        return doInTransaction(session -> session.createQuery(
                        "select m.time " + PENDING_MESSAGES_QUERY + " order by m.id", Long.class)
                .setMaxResults(1)
                .uniqueResultOptional()
                .map(Instant::ofEpochMilli));
    }

}
