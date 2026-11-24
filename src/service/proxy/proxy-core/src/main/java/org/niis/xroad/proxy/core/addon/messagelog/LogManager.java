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
package org.niis.xroad.proxy.core.addon.messagelog;

import ee.ria.xroad.common.DiagnosticStatus;
import ee.ria.xroad.common.DiagnosticsStatus;
import ee.ria.xroad.common.DiagnosticsUtils;
import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.message.AttachmentStream;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.TimeUtils;

import jakarta.xml.bind.JAXBException;
import jakarta.xml.soap.SOAPException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.BoundedInputStream;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.NodeProperties;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.messagelog.LogMessage;
import org.niis.xroad.messagelog.MessageLogDatabaseCtx;
import org.niis.xroad.messagelog.MessageRecord;
import org.niis.xroad.messagelog.RestLogMessage;
import org.niis.xroad.messagelog.SoapLogMessage;
import org.niis.xroad.messagelog.TimestampRecord;
import org.niis.xroad.proxy.core.configuration.ProxyMessageLogProperties;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static ee.ria.xroad.common.util.EncoderUtils.encodeBase64;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.niis.xroad.common.core.exception.ErrorCode.LOGGING_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.NO_TIMESTAMPING_PROVIDER_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.TIMESTAMPING_FAILED;

/**
 * Message log manager. Sets up the whole logging system components.
 * The logging system consists of a task queue, timestamper, archiver and log cleaner.
 */
@Slf4j
public class LogManager extends AbstractLogManager {

    protected final GlobalConfProvider globalConfProvider;
    protected final ServerConfProvider serverConfProvider;
    protected final LogRecordManager logRecordManager;
    protected final MessageLogDatabaseCtx messageLogDatabaseCtx;
    protected final ProxyMessageLogProperties messageLogProperties;

    private final Timestamper timestamper;
    // package private for testing
    final TimestamperJob timestamperJob;

    // package private for testing
    final TaskQueue taskQueue;

    public LogManager(GlobalConfProvider globalConfProvider, ServerConfProvider serverConfProvider,
                      LogRecordManager logRecordManager, MessageLogDatabaseCtx messageLogDatabaseCtx,
                      ProxyMessageLogProperties messageLogProperties) {
        super(globalConfProvider, serverConfProvider);

        this.globalConfProvider = globalConfProvider;
        this.serverConfProvider = serverConfProvider;
        this.logRecordManager = logRecordManager;
        this.messageLogDatabaseCtx = messageLogDatabaseCtx;
        this.messageLogProperties = messageLogProperties;
        this.timestamper = getTimestamperImpl();
        this.taskQueue = getTaskQueueImpl(timestamper);

        if (NodeProperties.isSecondaryNode()) {
            log.info("Periodic batch time-stamping is not started on a secondary cluster node.");
            this.timestamperJob = null;
        } else {
            this.timestamperJob = createTimestamperJob(taskQueue);
        }
    }

    public void destroy() {
        if (timestamperJob != null) {
            timestamperJob.shutdown();
        }
    }

    private TimestamperJob createTimestamperJob(TaskQueue taskQueueParam) {
        return new TimestamperJob(globalConfProvider, getTimestamperJobInitialDelay(), taskQueueParam,
                messageLogProperties.timestamper().retryDelay());
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
    public void log(LogMessage message) {
        try {
            boolean shouldTimestampImmediately = messageLogProperties.timestamper().timestampImmediately();

            verifyCanLogMessage(shouldTimestampImmediately);

            MessageRecord logRecord = switch (message) {
                case SoapLogMessage sm -> createMessageRecord(sm);
                case RestLogMessage rm -> createMessageRecord(rm);
            };

            logRecord = saveMessageRecord(logRecord);

            if (shouldTimestampImmediately) {
                timestampImmediately(logRecord);
            }
        } catch (Exception e) {
            throw XrdRuntimeException.systemException(e);
        }
    }

    @Override
    public TimestampRecord timestamp(Long messageRecordId) {
        log.trace("timestamp({})", messageRecordId);

        try {
            var messageRecord = (MessageRecord) logRecordManager.get(messageRecordId);

            if (messageRecord.getTimestampRecord() != null) {
                return messageRecord.getTimestampRecord();
            } else {
                TimestampRecord timestampRecord = timestampImmediately(messageRecord);
                handleTimestampJob(true);

                return timestampRecord;
            }
        } catch (Exception e) {
            throw XrdRuntimeException.systemException(e);
        }
    }

    @Override
    public Map<String, DiagnosticsStatus> getDiagnosticStatus() {
        return statusMap;
    }

    // ------------------------------------------------------------------------

    protected TaskQueue getTaskQueueImpl(Timestamper timestamperParam) {
        return new TaskQueue(timestamperParam, this, messageLogDatabaseCtx, messageLogProperties.timestamper());
    }

    protected Timestamper getTimestamperImpl() {
        return new Timestamper(globalConfProvider, serverConfProvider, logRecordManager, messageLogProperties);
    }

    private TimestampRecord timestampImmediately(MessageRecord logRecord) {
        log.trace("timestampImmediately({})", logRecord);

        Timestamper.TimestampResult result = timestamper.handleTimestampTask(new Timestamper.TimestampTask(logRecord));

        switch (result) {
            case Timestamper.TimestampSucceeded tts:
                return saveTimestampRecord(tts);
            case Timestamper.TimestampFailed ttf:
                Exception e = ttf.getCause();
                log.error("Timestamping failed", e);
                putStatusMapFailures(ttf);
                throw XrdRuntimeException.systemException(e);
            default:
                throw XrdRuntimeException.systemInternalError("Unexpected result from Timestamper: " + result.getClass());
        }
    }

    private MessageRecord createMessageRecord(SoapLogMessage message)
            throws IOException, SOAPException, JAXBException, IllegalAccessException {
        log.trace("createMessageRecord()");

        var manipulator = new MessageBodyManipulator(messageLogProperties);

        MessageRecord messageRecord = new MessageRecord(
                message.getQueryId(),
                manipulator.getLoggableMessageText(message),
                message.getSignature().getSignatureXml(),
                message.isResponse(),
                message.isClientSide() ? message.getClient() : message.getService().getClientId(),
                message.getXRequestId());

        messageRecord.setTime(new Date().getTime());

        if (message.getSignature().isBatchSignature()) {
            messageRecord.setHashChainResult(message.getSignature().getHashChainResult());
            messageRecord.setHashChain(message.getSignature().getHashChain());
        } else if (manipulator.isBodyLogged(message)) {
            // log attachments for non-batch signatures
            if (messageLogProperties.maxLoggableMessageBodySize() > 0) {
                messageRecord.setAttachmentStreams(message.getAttachments()
                        .stream().map(this::boundedAttachmentStream).toList());
            }

        }

        messageRecord.setSignatureHash(signatureHash(message.getSignature().getSignatureXml()));
        return messageRecord;
    }

    private AttachmentStream boundedAttachmentStream(AttachmentStream attachment) {
        return new AttachmentStream() {
            @Override
            public InputStream getStream() {
                if (attachment.getSize() > messageLogProperties.maxLoggableMessageBodySize()
                        && !messageLogProperties.truncatedBodyAllowed()) {
                    throw XrdRuntimeException.systemException(LOGGING_FAILED, "Message attachment size exceeds maximum loggable size");
                }
                final BoundedInputStream body = new BoundedInputStream(attachment.getStream(),
                        messageLogProperties.maxLoggableMessageBodySize());
                body.setPropagateClose(false);
                return body;
            }

            @Override
            public long getSize() {
                return attachment.getSize();
            }
        };
    }

    private MessageRecord createMessageRecord(RestLogMessage message) throws IOException {
        log.trace("createMessageRecord()");

        final MessageBodyManipulator manipulator = new MessageBodyManipulator(messageLogProperties);
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
                && messageLogProperties.maxLoggableMessageBodySize() > 0
                && manipulator.isBodyLogged(message)) {
            if (message.getBody().size() > messageLogProperties.maxLoggableMessageBodySize()
                    && !messageLogProperties.truncatedBodyAllowed()) {
                throw XrdRuntimeException.systemException(LOGGING_FAILED, "Message size exceeds maximum loggable size");
            }
            final BoundedInputStream body = BoundedInputStream.builder()
                    .setInputStream(message.getBody())
                    .setMaxCount(messageLogProperties.maxLoggableMessageBodySize())
                    .setPropagateClose(false)
                    .get();
            messageRecord.setAttachmentStream(body, Math.min(message.getBody().size(),
                    messageLogProperties.maxLoggableMessageBodySize()));
        }

        if (message.getSignature().isBatchSignature()) {
            messageRecord.setHashChainResult(message.getSignature().getHashChainResult());
            messageRecord.setHashChain(message.getSignature().getHashChain());
        }

        messageRecord.setSignatureHash(signatureHash(message.getSignature().getSignatureXml()));
        return messageRecord;
    }

    protected MessageRecord saveMessageRecord(MessageRecord messageRecord) {
        logRecordManager.saveMessageRecord(messageRecord);
        return messageRecord;
    }

    TimestampRecord saveTimestampRecord(Timestamper.TimestampSucceeded message) {
        log.trace("saveTimestampRecord()");

        putStatusMapSuccess(message.getUrl());

        TimestampRecord timestampRecord = createTimestampRecord(message);
        logRecordManager.saveTimestampRecord(timestampRecord, message.getMessageRecords(), message.getHashChains());

        return timestampRecord;
    }

    /**
     * Put success state into statusMap (used for diagnostics) for a given TSA url
     *
     * @param url url of timestamper which stamped successfully
     */
    static void putStatusMapSuccess(String url) {
        statusMap.put(url, new DiagnosticsStatus(DiagnosticStatus.OK, TimeUtils.offsetDateTimeNow()));
    }

    void putStatusMapFailures(Timestamper.TimestampFailed timestampFailedResult) {
        timestampFailedResult.getErrorsByUrl().forEach((tspUrl, ex) -> {
            ErrorCode errorCode = DiagnosticsUtils.getErrorCode(ex);
            DiagnosticsStatus diagnosticsStatus =
                    new DiagnosticsStatus(DiagnosticStatus.ERROR, TimeUtils.offsetDateTimeNow(), tspUrl, errorCode);
            diagnosticsStatus.setErrorCodeMetadata(DiagnosticsUtils.getErrorCodeMetadata(ex));
            statusMap.put(tspUrl, diagnosticsStatus);
        });
    }

    private static TimestampRecord createTimestampRecord(Timestamper.TimestampSucceeded message) {
        TimestampRecord timestampRecord = new TimestampRecord();
        timestampRecord.setTime(new Date().getTime());
        timestampRecord.setTimestamp(encodeBase64(message.getTimestampDer()));
        timestampRecord.setHashChainResult(message.getHashChainResult());

        return timestampRecord;
    }

    void setTimestampingStatus(boolean succeeded) {
        handleTimestampJob(succeeded);
    }

    private void handleTimestampJob(boolean succeeded) {
        if (timestamperJob != null) {
            if (succeeded) {
                timestamperJob.onSuccess();
            } else {
                timestamperJob.onFailure();
            }
        }
    }

    // package private for testing
    void verifyCanLogMessage(boolean shouldTimestampImmediately) {
        if (serverConfProvider.getTspUrls().isEmpty()) {
            throw XrdRuntimeException.systemException(NO_TIMESTAMPING_PROVIDER_FOUND)
                    .details("Cannot time-stamp messages: no timestamping services configured")
                    .build();
        }

        if (shouldTimestampImmediately) {
            return;
        }

        int period = messageLogProperties.timestamper().acceptableTimestampFailurePeriod();

        if (period == 0) { // check disabled
            return;
        }

        verifyTimestampingNotFailed(period);
    }

    /**
     * Refuses when the oldest message still waiting for a time-stamp was logged earlier than the
     * acceptable period. Every node reads the same message log database, so primary and secondary
     * nodes make the same decision, also when the primary is down. A failed read throws, so the
     * message is refused.
     */
    private void verifyTimestampingNotFailed(int period) {
        Instant oldestAllowed = TimeUtils.now().minusSeconds(period);

        if (messageLogDatabaseCtx.findOldestTimestampTaskTime().filter(oldestAllowed::isAfter).isPresent()) {
            throw XrdRuntimeException.systemException(TIMESTAMPING_FAILED)
                    .details("Cannot time-stamp messages")
                    .build();
        }
    }

    private String signatureHash(String signatureXml) throws IOException {
        return CryptoUtils.signatureHash(signatureXml, DigestAlgorithm.ofName(messageLogProperties.hashAlgoIdStr()));
    }

    /**
     * Timestamper job is responsible for firing up the timestamping periodically.
     */
    public static class TimestamperJob {
        private static final int MIN_INTERVAL_SECONDS = 60;
        private static final int MAX_INTERVAL_SECONDS = 60 * 60 * 24;
        private final int timestampRetryDelaySeconds;

        // Flag for indicating backoff retry state
        private final AtomicBoolean retryMode = new AtomicBoolean(false);

        private final GlobalConfProvider globalConfProvider;
        private final ScheduledExecutorService taskScheduler;
        private final TaskQueue taskQueue;
        private ScheduledFuture<?> scheduledTask;

        public TimestamperJob(GlobalConfProvider globalConfProvider, Duration initialDelay, TaskQueue taskQueue,
                              int timestampRetryDelaySeconds) {
            this(globalConfProvider, initialDelay, taskQueue, timestampRetryDelaySeconds, Executors.newSingleThreadScheduledExecutor());
        }

        // package private for testing
        TimestamperJob(GlobalConfProvider globalConfProvider, Duration initialDelay, TaskQueue taskQueue,
                       int timestampRetryDelaySeconds, ScheduledExecutorService taskScheduler) {
            this.globalConfProvider = globalConfProvider;
            this.timestampRetryDelaySeconds = timestampRetryDelaySeconds;
            log.trace("Initializing TimestamperJob");
            this.taskQueue = taskQueue;
            this.taskScheduler = taskScheduler;
            schedule(initialDelay, this::handleStartTimestamping);
        }

        /**
         * Leaves retry mode and runs a batch at once. Does nothing outside retry mode, so the
         * success of the batch it starts does not start another one.
         */
        void onSuccess() {
            if (!retryMode.compareAndSet(true, false)) {
                return;
            }
            log.info("Batch time-stamping refresh cycle successfully completed, continuing with normal scheduling");
            this.taskQueue.handleStartTimestamping();
        }

        void onFailure() {
            if (!retryMode.compareAndSet(false, true)) {
                return;
            }
            log.info("Batch time-stamping failed, switching to retry backoff schedule");
            log.info("Time-stamping retry delay value is: {}s", timestampRetryDelaySeconds);
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
            if (retryMode.get()) {
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
            log.debug("Use batch time-stamping retry backoff schedule: {}", retryMode.get());

            try {
                actualInterval = globalConfProvider.getTimestampingIntervalSeconds();
            } catch (Exception e) {
                log.error("Failed to get timestamping interval", e);
            }

            if (retryMode.get()) {
                actualInterval = (timestampRetryDelaySeconds < actualInterval && timestampRetryDelaySeconds > 0)
                        ? timestampRetryDelaySeconds : actualInterval;
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
