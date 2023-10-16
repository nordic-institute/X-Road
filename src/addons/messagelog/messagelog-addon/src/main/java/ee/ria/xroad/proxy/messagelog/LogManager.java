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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.DiagnosticsErrorCodes;
import ee.ria.xroad.common.DiagnosticsStatus;
import ee.ria.xroad.common.DiagnosticsUtils;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.messagelog.AbstractLogManager;
import ee.ria.xroad.common.messagelog.LogMessage;
import ee.ria.xroad.common.messagelog.MessageLogProperties;
import ee.ria.xroad.common.messagelog.MessageRecord;
import ee.ria.xroad.common.messagelog.RestLogMessage;
import ee.ria.xroad.common.messagelog.SoapLogMessage;
import ee.ria.xroad.common.messagelog.TimestampRecord;
import ee.ria.xroad.common.util.JobManager;
import ee.ria.xroad.common.util.TimeUtils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.BoundedInputStream;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import static ee.ria.xroad.common.ErrorCodes.X_LOGGING_FAILED_X;
import static ee.ria.xroad.common.ErrorCodes.X_MLOG_TIMESTAMPER_FAILED;
import static ee.ria.xroad.common.messagelog.MessageLogProperties.getAcceptableTimestampFailurePeriodSeconds;
import static ee.ria.xroad.common.messagelog.MessageLogProperties.getHashAlg;
import static ee.ria.xroad.common.messagelog.MessageLogProperties.getTimestampRetryDelay;
import static ee.ria.xroad.common.messagelog.MessageLogProperties.shouldTimestampImmediately;
import static ee.ria.xroad.common.util.CryptoUtils.calculateDigest;
import static ee.ria.xroad.common.util.CryptoUtils.encodeBase64;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Message log manager. Sets up the whole logging system components.
 * The logging system consists of a task queue, timestamper, archiver and log cleaner.
 */
@Slf4j
public class LogManager extends AbstractLogManager {

    static final long MAX_LOGGABLE_BODY_SIZE = MessageLogProperties.getMaxLoggableBodySize();
    static final boolean TRUNCATED_BODY_ALLOWED = MessageLogProperties.isTruncatedBodyAllowed();

    // Date at which a time-stamping first failed.
    private Instant timestampFailed;

    private final Timestamper timestamper;
    private final TimestamperJob timestamperJob;

    // package private for testing
    final TaskQueue taskQueue;

    LogManager(JobManager jobManager) {
        super(jobManager);

        timestamper = getTimestamperImpl();
        taskQueue = getTaskQueueImpl(timestamper);
        timestamperJob = createTimestamperJob(taskQueue);
    }

    @Override
    public void shutdown() {
        timestamperJob.shutdown();
        super.shutdown();
    }

    private TimestamperJob createTimestamperJob(TaskQueue taskQueueParam) {
        return new TimestamperJob(getTimestamperJobInitialDelay(), taskQueueParam);
    }

    /**
     * Can be overwritten in test classes if we want to make sure that timestamping does not start prematurely.
     *
     * @return timestamper job initial delay.
     */
    protected Duration getTimestamperJobInitialDelay() {
        return Duration.of(1, ChronoUnit.SECONDS);
    }

    // ------------------------------------------------------------------------

    @Override
    public void log(LogMessage message) throws Exception {
        boolean shouldTimestampImmediately = shouldTimestampImmediately();

        verifyCanLogMessage(shouldTimestampImmediately);

        MessageRecord logRecord;
        if (message instanceof SoapLogMessage) {
            logRecord = createMessageRecord((SoapLogMessage) message);
        } else {
            logRecord = createMessageRecord((RestLogMessage) message);
        }
        logRecord = saveMessageRecord(logRecord);

        if (shouldTimestampImmediately) {
            timestampImmediately(logRecord);
        }
    }

    @Override
    public TimestampRecord timestamp(Long messageRecordId) throws Exception {
        log.trace("timestamp({})", messageRecordId);

        MessageRecord record = (MessageRecord) LogRecordManager.get(messageRecordId);

        if (record.getTimestampRecord() != null) {
            return record.getTimestampRecord();
        } else {
            TimestampRecord timestampRecord = timestampImmediately(record);
            // Avoid blocking the message logging (in non-timestamp-immediately mode) in case the last periodical
            // timestamping task failed and currently the task queue got empty, but no more messages are logged until
            // the acceptable timestamp failure period is reached.
            setTimestampSucceeded();

            return timestampRecord;
        }
    }

    @Override
    public Map<String, DiagnosticsStatus> getDiagnosticStatus() {
        return statusMap;
    }

    // ------------------------------------------------------------------------

    protected TaskQueue getTaskQueueImpl(Timestamper timestamperParam) {
        return new TaskQueue(timestamperParam, this);
    }

    protected Timestamper getTimestamperImpl() {
        return new Timestamper();
    }

    private TimestampRecord timestampImmediately(MessageRecord logRecord) throws Exception {
        log.trace("timestampImmediately({})", logRecord);

        Timestamper.TimestampResult result = timestamper.handleTimestampTask(new Timestamper.TimestampTask(logRecord));

        if (result instanceof Timestamper.TimestampSucceeded) {
            return saveTimestampRecord((Timestamper.TimestampSucceeded) result);
        } else if (result instanceof Timestamper.TimestampFailed) {
            Exception e = ((Timestamper.TimestampFailed) result).getCause();

            log.error("Timestamping failed", e);

            putStatusMapFailures(e);

            throw e;
        } else {
            throw new RuntimeException("Unexpected result from Timestamper: " + result.getClass());
        }
    }

    private static MessageRecord createMessageRecord(SoapLogMessage message) throws Exception {
        log.trace("createMessageRecord()");

        String loggedMessage = new MessageBodyManipulator().getLoggableMessageText(message);

        MessageRecord messageRecord = new MessageRecord(
                message.getQueryId(),
                loggedMessage,
                message.getSignature().getSignatureXml(),
                message.isResponse(),
                message.isClientSide() ? message.getClient() : message.getService().getClientId(),
                message.getXRequestId());

        messageRecord.setTime(new Date().getTime());

        if (message.getSignature().isBatchSignature()) {
            messageRecord.setHashChainResult(message.getSignature().getHashChainResult());
            messageRecord.setHashChain(message.getSignature().getHashChain());
        }

        messageRecord.setSignatureHash(signatureHash(message.getSignature().getSignatureXml()));
        return messageRecord;
    }

    private static MessageRecord createMessageRecord(RestLogMessage message) throws Exception {
        log.trace("createMessageRecord()");

        final MessageBodyManipulator manipulator = new MessageBodyManipulator();
        MessageRecord messageRecord = new MessageRecord(
                message.getQueryId(),
                manipulator.getLoggableMessageText(message),
                message.getSignature().getSignatureXml(),
                message.isResponse(),
                message.isClientSide() ? message.getClient() : message.getService().getClientId(),
                message.getXRequestId());

        messageRecord.setTime(new Date().getTime());

        if (message.getBody() != null
                && message.getBody().size() > 0
                && MAX_LOGGABLE_BODY_SIZE > 0
                && manipulator.isBodyLogged(message)) {
            if (message.getBody().size() > MAX_LOGGABLE_BODY_SIZE && !TRUNCATED_BODY_ALLOWED) {
                throw new CodedException(X_LOGGING_FAILED_X, "Message size exceeds maximum loggable size");
            }
            final BoundedInputStream body = new BoundedInputStream(message.getBody(), MAX_LOGGABLE_BODY_SIZE);
            body.setPropagateClose(false);
            messageRecord.setAttachmentStream(body, Math.min(message.getBody().size(), MAX_LOGGABLE_BODY_SIZE));
        }

        if (message.getSignature().isBatchSignature()) {
            messageRecord.setHashChainResult(message.getSignature().getHashChainResult());
            messageRecord.setHashChain(message.getSignature().getHashChain());
        }

        messageRecord.setSignatureHash(signatureHash(message.getSignature().getSignatureXml()));
        return messageRecord;
    }

    protected MessageRecord saveMessageRecord(MessageRecord messageRecord) throws Exception {
        LogRecordManager.saveMessageRecord(messageRecord);
        return messageRecord;
    }

    static TimestampRecord saveTimestampRecord(Timestamper.TimestampSucceeded message) throws Exception {
        log.trace("saveTimestampRecord()");

        putStatusMapSuccess(message.getUrl());

        TimestampRecord timestampRecord = createTimestampRecord(message);
        LogRecordManager.saveTimestampRecord(timestampRecord, message.getMessageRecords(), message.getHashChains());

        return timestampRecord;
    }

    /**
     * Put success state into statusMap (used for diagnostics) for a given TSA url
     *
     * @param url url of timestamper which stamped successfully
     */
    static void putStatusMapSuccess(String url) {
        statusMap.put(url, new DiagnosticsStatus(DiagnosticsErrorCodes.RETURN_SUCCESS, TimeUtils.offsetDateTimeNow()));
    }

    /**
     * Put failure state into statusMap (used for diagnostics).
     * Timestamping ({@link AbstractTimestampRequest} attempts to use all TSAs, and failure means that
     * all were tried and failed, so all TSAs will be marked with failed status
     *
     * @param e exception which is used to determine diagnostics error code
     */
    static void putStatusMapFailures(Exception e) {
        int errorCode = DiagnosticsUtils.getErrorCode(e);
        for (String tspUrl : ServerConf.getTspUrl()) {
            statusMap.put(tspUrl,
                    new DiagnosticsStatus(errorCode, TimeUtils.offsetDateTimeNow(), tspUrl));
        }
    }


    private static TimestampRecord createTimestampRecord(Timestamper.TimestampSucceeded message) {
        TimestampRecord timestampRecord = new TimestampRecord();
        timestampRecord.setTime(new Date().getTime());
        timestampRecord.setTimestamp(encodeBase64(message.getTimestampDer()));
        timestampRecord.setHashChainResult(message.getHashChainResult());

        return timestampRecord;
    }

    boolean isTimestampFailed() {
        return timestampFailed != null;
    }

    void setTimestampingStatus(SetTimestampingStatusMessage statusMessage) {
        if (statusMessage.getStatus() == SetTimestampingStatusMessage.Status.SUCCESS) {
            setTimestampSucceeded();
        } else {
            setTimestampFailed(statusMessage.getAtTime());
        }
    }

    /**
     * Only externally use this method from tests. Otherwise send message to this actor.
     */
    void setTimestampSucceeded() {
        if (timestampFailed != null) {
            timestampFailed = null;
            this.timestamperJob.onSuccess();
        }
    }

    void setTimestampFailed(Instant atTime) {
        if (timestampFailed == null) {
            timestampFailed = atTime;
            this.timestamperJob.onFailure();
        }
    }

    private void verifyCanLogMessage(boolean shouldTimestampImmediately) {
        if (ServerConf.getTspUrl().isEmpty()) {
            throw new CodedException(X_MLOG_TIMESTAMPER_FAILED,
                    "Cannot time-stamp messages: no timestamping services configured");
        }

        if (!shouldTimestampImmediately) {
            int period = getAcceptableTimestampFailurePeriodSeconds();

            if (period == 0) { // check disabled
                return;
            }

            if (isTimestampFailed()) {
                if (TimeUtils.now().minusSeconds(period).isAfter(timestampFailed)) {
                    throw new CodedException(X_MLOG_TIMESTAMPER_FAILED, "Cannot time-stamp messages");
                }
            }
        }
    }

    static String signatureHash(String signatureXml) throws Exception {
        return encodeBase64(getInputHash(signatureXml));
    }

    private static byte[] getInputHash(String str) throws Exception {
        return calculateDigest(getHashAlg(), str.getBytes(UTF_8));
    }

    /**
     * Timestamper job is responsible for firing up the timestamping periodically.
     */
    public static class TimestamperJob {
        private static final int MIN_INTERVAL_SECONDS = 60;
        private static final int MAX_INTERVAL_SECONDS = 60 * 60 * 24;
        private static final int TIMESTAMP_RETRY_DELAY_SECONDS = getTimestampRetryDelay();

        // Flag for indicating backoff retry state
        private boolean retryMode = false;

        private final ScheduledExecutorService taskScheduler;
        private final TaskQueue taskQueue;
        private ScheduledFuture<?> scheduledTask;

        public TimestamperJob(Duration initialDelay, TaskQueue taskQueue) {
            log.trace("Initializing TimestamperJob");
            this.taskQueue = taskQueue;
            this.taskScheduler = Executors.newSingleThreadScheduledExecutor();
            schedule(initialDelay, this::handleStartTimestamping);
        }

        void onSuccess() {
            log.info("Batch time-stamping refresh cycle successfully completed, continuing with normal scheduling");
            // Move back into normal state.
            // Cancel next tick, run a batch immediately and schedule next one.
            retryMode = false;
            this.taskQueue.handleStartTimestamping();
        }

        void onFailure() {
            log.info("Batch time-stamping failed, switching to retry backoff schedule");
            log.info("Time-stamping retry delay value is: {}s", TIMESTAMP_RETRY_DELAY_SECONDS);
            // Move into recover-from-failed state.
            // Cancel next tick and start backoff schedule.
            retryMode = true;
        }

        private void handleStartTimestamping() {
            log.trace("handleStartTimestamping()");
            try {
                this.taskQueue.handleStartTimestamping();
            } finally {
                scheduleNext();
            }
        }

        private void handleStartTimestampingRetryMode() {
            log.trace("handleStartTimestamping()");
            try {
                this.taskQueue.handleStartTimestampingRetryMode();
            } finally {
                scheduleNext();
            }
        }

        private void scheduleNext() {
            if (retryMode) {
                schedule(getNextDelay(), this::handleStartTimestampingRetryMode);
            } else {
                schedule(getNextDelay(), this::handleStartTimestamping);
            }
        }

        public void shutdown() {
            log.trace("shutdown()");
            cancelNext();
            taskScheduler.shutdown();
        }

        private void schedule(Duration delay, Runnable runnable) {
            cancelNext();
            this.scheduledTask = taskScheduler.schedule(runnable, delay.getSeconds(), SECONDS);
        }

        private Duration getNextDelay() {
            int actualInterval = MIN_INTERVAL_SECONDS;
            log.debug("Use batch time-stamping retry backoff schedule: {}", retryMode);

            try {
                actualInterval = GlobalConf.getTimestampingIntervalSeconds();
            } catch (Exception e) {
                log.error("Failed to get timestamping interval", e);
            }

            if (retryMode) {
                actualInterval = (TIMESTAMP_RETRY_DELAY_SECONDS < actualInterval && TIMESTAMP_RETRY_DELAY_SECONDS > 0)
                        ? TIMESTAMP_RETRY_DELAY_SECONDS : actualInterval;
            }

            int intervalSeconds = Math.min(Math.max(actualInterval, MIN_INTERVAL_SECONDS), MAX_INTERVAL_SECONDS);
            log.debug("Time-stamping interval is: {}s", intervalSeconds);

            return Duration.of(intervalSeconds, ChronoUnit.SECONDS);
        }

        protected void cancelNext() {
            if (scheduledTask != null) {
                if (!scheduledTask.isCancelled()) {
                    boolean result = scheduledTask.cancel(false);
                    log.trace("cancelNext called, cancel() return value: {}", result);
                }
            }
        }
    }
}
