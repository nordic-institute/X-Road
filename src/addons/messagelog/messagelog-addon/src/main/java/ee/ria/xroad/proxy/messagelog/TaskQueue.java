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
package ee.ria.xroad.proxy.messagelog;

import ee.ria.xroad.common.messagelog.MessageLogProperties;
import ee.ria.xroad.proxy.messagelog.Timestamper.TimestampFailed;
import ee.ria.xroad.proxy.messagelog.Timestamper.TimestampSucceeded;
import ee.ria.xroad.proxy.messagelog.Timestamper.TimestampTask;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;

import java.util.Arrays;
import java.util.List;

import static ee.ria.xroad.proxy.messagelog.MessageLogDatabaseCtx.doInTransaction;

/**
 * Handles the TaskQueues -- adds tasks to the queue and sends the active queue for time-stamping.
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class TaskQueue {

    static final double TIMESTAMPED_RECORDS_RATIO_THRESHOLD = 0.7;
    static final int TIMESTAMP_RECORDS_LIMIT_RETRY_MODE = 1;

    private final Timestamper timestamper;
    private final LogManager logManager;

    protected void handleTimestampSucceeded(TimestampSucceeded message) {
        log.trace("handleTimestampSucceeded");

        if (log.isTraceEnabled()) {
            log.trace("Time-stamped message records {}", Arrays.toString(message.getMessageRecords()));
        }

        boolean succeeded = true;
        Exception failureCause = null;

        try {
            saveTimestampRecord(message);
        } catch (Exception e) {
            log.error("Failed to save time-stamp record to database", e);

            succeeded = false;
        } finally {
            if (succeeded) {
                indicateSuccess();
                // If time-stamped records count equals to time-stamp records limit, there are probably
                // still records to be time-stamped. Init another another time-stamping round to prevent
                // messagelog records to begin to bloat.
                if (message.getMessageRecords().length == MessageLogProperties.getTimestampRecordsLimit()) {
                    log.info("Time-stamped records count equaled to time-stamp records limit");
                    handleStartTimestamping();
                }
            } else {
                indicateFailure(failureCause);
            }
        }
    }

    protected void saveTimestampRecord(TimestampSucceeded message) throws Exception {
        LogManager.saveTimestampRecord(message);
    }

    private void indicateSuccess() {
        sendTimestampingStatusToLogManager(SetTimestampingStatusMessage.Status.SUCCESS);
    }

    /**
     * @param cause possible exception that caused the failure. Used for diagnostics error code.
     */
    private void indicateFailure(Exception cause) {
        // If the timestamping task queue is currently empty, it means some previous timestamping task was successful
        // already. In that case do not indicate failure to the LogManager, otherwise the message logging may block
        // (in non-timestamp-immediately mode) in case further no more messages are logged until the acceptable
        // timestamp failure period is reached.
        if (isTaskQueueEmpty()) {
            return;
        }

        // set diagnostics status
        LogManager.putStatusMapFailures(cause);

        sendTimestampingStatusToLogManager(SetTimestampingStatusMessage.Status.FAILURE);
    }

    /**
     * Sends timestamping status message to LogManager.
     *
     * @param status timestamping status message.
     */
    private void sendTimestampingStatusToLogManager(SetTimestampingStatusMessage.Status status) {
        logManager.setTimestampingStatus(new SetTimestampingStatusMessage(status));
    }

    protected void handleTimestampFailed(TimestampFailed message) {
        log.trace("handleTimestampFailed");

        indicateFailure(message.getCause());
    }

    protected void handleStartTimestamping() {
        handleStartTimestamping(MessageLogProperties.getTimestampRecordsLimit());
    }

    protected void handleStartTimestampingRetryMode() {
        handleStartTimestamping(TIMESTAMP_RECORDS_LIMIT_RETRY_MODE);
    }

    private void handleStartTimestamping(int timestampRecordsLimit) {
        List<Task> timestampTasks;

        try {
            timestampTasks = doInTransaction(session -> getTimestampTasks(session, timestampRecordsLimit));
        } catch (Exception e) {
            log.error("Error getting time-stamp tasks", e);

            return;
        }

        if (timestampTasks.isEmpty()) {
            log.trace("Nothing to time-stamp, task queue is empty");
            indicateSuccess();
            return;
        }

        int timestampTasksSize = timestampTasks.size();

        log.info("Start time-stamping {} message records", timestampTasksSize);

        if (timestampTasksSize / (double) MessageLogProperties.getTimestampRecordsLimit()
                >= TIMESTAMPED_RECORDS_RATIO_THRESHOLD) {
            log.warn("Number of time-stamped records is over {} % of 'timestamp-records-limit' value",
                    TIMESTAMPED_RECORDS_RATIO_THRESHOLD * 100);
        }

        final Timestamper.TimestampResult timestampResult = timestamper
                .handleTimestampTask(createTimestampTask(timestampTasks));
        if (timestampResult instanceof TimestampSucceeded) {
            handleTimestampSucceeded((TimestampSucceeded) timestampResult);
        } else if (timestampResult instanceof TimestampFailed) {
            handleTimestampFailed((TimestampFailed) timestampResult);
        }

    }

    private TimestampTask createTimestampTask(List<Task> timestampTasks) {
        Long[] messageRecords = new Long[timestampTasks.size()];
        String[] signatureHashes = new String[timestampTasks.size()];

        for (int i = 0; i < timestampTasks.size(); i++) {
            messageRecords[i] = timestampTasks.get(i).getMessageRecordNo();
            signatureHashes[i] = timestampTasks.get(i).getSignatureHash();
        }

        return new TimestampTask(messageRecords, signatureHashes);
    }

    private static boolean isTaskQueueEmpty() {
        try {
            return doInTransaction(TaskQueue::getTasksQueueSize) == 0L;
        } catch (Exception e) {
            log.error("Could not read timestamp task queue status", e);

            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Task> getTimestampTasks(Session session, int timestampRecordsLimit) {
        return session.createQuery(getTaskQueueQuery()).setMaxResults(timestampRecordsLimit).list();
    }

    private static Long getTasksQueueSize(Session session) {
        return (Long) session.createQuery(getTaskQueueSizeQuery()).uniqueResult();
    }

    static String getTaskQueueQuery() {
        return "select new " + Task.class.getName() + "(m.id, m.signatureHash) "
                + "from MessageRecord m where m.timestampRecord is null order by m.id";
    }

    private static String getTaskQueueSizeQuery() {
        return "select COUNT(*) from MessageRecord m where m.timestampRecord is null";
    }
}
