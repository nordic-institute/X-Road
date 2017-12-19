/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
import ee.ria.xroad.common.CommonMessages;
import ee.ria.xroad.common.DiagnosticsErrorCodes;
import ee.ria.xroad.common.DiagnosticsStatus;
import ee.ria.xroad.common.DiagnosticsUtils;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.messagelog.AbstractLogManager;
import ee.ria.xroad.common.messagelog.LogRecord;
import ee.ria.xroad.common.messagelog.MessageLogProperties;
import ee.ria.xroad.common.messagelog.MessageRecord;
import ee.ria.xroad.common.messagelog.TimestampRecord;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.util.JobManager;
import ee.ria.xroad.common.util.MessageSendingJob;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.pattern.Patterns;
import akka.util.Timeout;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.quartz.JobDataMap;
import org.quartz.SchedulerException;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.nio.file.Paths;
import java.time.LocalTime;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static ee.ria.xroad.common.ErrorCodes.X_MLOG_TIMESTAMPER_FAILED;
import static ee.ria.xroad.common.messagelog.MessageLogProperties.getAcceptableTimestampFailurePeriodSeconds;
import static ee.ria.xroad.common.messagelog.MessageLogProperties.getArchiveInterval;
import static ee.ria.xroad.common.messagelog.MessageLogProperties.getCleanInterval;
import static ee.ria.xroad.common.messagelog.MessageLogProperties.getHashAlg;
import static ee.ria.xroad.common.messagelog.MessageLogProperties.shouldTimestampImmediately;
import static ee.ria.xroad.common.util.CryptoUtils.calculateDigest;
import static ee.ria.xroad.common.util.CryptoUtils.encodeBase64;
import static ee.ria.xroad.proxy.messagelog.LogArchiver.START_ARCHIVING;
import static ee.ria.xroad.proxy.messagelog.LogCleaner.START_CLEANING;
import static ee.ria.xroad.proxy.messagelog.TaskQueue.START_TIMESTAMPING;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Message log manager. Sets up the whole logging system components.
 * The logging system consists of a task queue, timestamper, archiver and log cleaner.
 */
@Slf4j
public class LogManager extends AbstractLogManager {

    private static final Timeout TIMESTAMP_TIMEOUT = new Timeout(Duration.create(30, TimeUnit.SECONDS));

    // Actor names of message log components
    static final String TASK_QUEUE_NAME = "RequestLogTaskQueue";
    static final String TIMESTAMPER_NAME = "RequestLogTimestamper";
    static final String ARCHIVER_NAME = "RequestLogArchiver";
    static final String CLEANER_NAME = "RequestLogCleaner";

    // Date at which a time-stamping first failed.
    private DateTime timestampFailed;

    private ActorRef timestamper;

    LogManager(JobManager jobManager) throws Exception {
        super(jobManager);

        createTaskQueue();

        createTimestamper();

        createArchiver(jobManager);
        createCleaner(jobManager);
    }

    @Getter
    private ActorRef taskQueueRef;

    private void createTaskQueue() {
        taskQueueRef = getContext().actorOf(getTaskQueueImpl(), TASK_QUEUE_NAME);
    }

    private void createTimestamper() {
        timestamper = getContext().actorOf(getTimestamperImpl(), TIMESTAMPER_NAME);

        getContext().actorOf(Props.create(TimestamperJob.class, getTimestamperJobInitialDelay()));
    }

    /**
     * Can be overwritten in test classes if we want to make sure that timestamping does not start prematurely.
     * @return timestamper job initial delay.
     */
    protected FiniteDuration getTimestamperJobInitialDelay() {
        return Duration.create(1, TimeUnit.SECONDS);
    }

    private void createArchiver(JobManager jobManager) {
        getContext().actorOf(getArchiverImpl(), ARCHIVER_NAME);

        registerCronJob(jobManager, ARCHIVER_NAME, START_ARCHIVING, getArchiveInterval());
    }

    private void createCleaner(JobManager jobManager) {
        getContext().actorOf(getCleanerImpl(), CLEANER_NAME);

        registerCronJob(jobManager, CLEANER_NAME, START_CLEANING, getCleanInterval());
    }

    // ------------------------------------------------------------------------

    @Override
    protected void log(SoapMessageImpl message, SignatureData signature, boolean clientSide) throws Exception {
        boolean shouldTimestampImmediately = shouldTimestampImmediately();

        verifyCanLogMessage(shouldTimestampImmediately);

        MessageRecord logRecord = saveMessageRecord(message, signature, clientSide);

        if (shouldTimestampImmediately) {
            timestampImmediately(logRecord);
        }
    }

    @Override
    protected TimestampRecord timestamp(Long messageRecordId) throws Exception {
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
    protected LogRecord findByQueryId(String queryId, Date startTime, Date endTime) throws Exception {
        log.trace("findByQueryId({}, {}, {})", queryId, startTime, endTime);

        return LogRecordManager.getByQueryId(queryId, startTime, endTime);
    }

    @Override
    public void onReceive(Object message) throws Exception {
        log.trace("onReceive({})", message);

        try {
            if (message instanceof String && CommonMessages.TIMESTAMP_STATUS.equals(message)) {
                getSender().tell(statusMap, getSelf());
            } else if (message instanceof SetTimestampingStatusMessage) {
                setTimestampingStatus((SetTimestampingStatusMessage) message);
            } else {
                super.onReceive(message);
            }
        } catch (Exception e) {
            getSender().tell(e, getSelf());
        }
    }

    // ------------------------------------------------------------------------

    protected Props getTaskQueueImpl() {
        return Props.create(TaskQueue.class);
    }

    protected Props getTimestamperImpl() {
        return Props.create(Timestamper.class);
    }

    protected Props getArchiverImpl() {
        return Props.create(LogArchiver.class, Paths.get(MessageLogProperties.getArchivePath()),
                Paths.get(SystemProperties.getTempFilesPath()));
    }

    protected Props getCleanerImpl() {
        return Props.create(LogCleaner.class);
    }

    private TimestampRecord timestampImmediately(MessageRecord logRecord) throws Exception {
        log.trace("timestampImmediately({})", logRecord);

        Object result = Await.result(Patterns.ask(timestamper, new Timestamper.TimestampTask(logRecord),
                TIMESTAMP_TIMEOUT), TIMESTAMP_TIMEOUT.duration());

        if (result instanceof Timestamper.TimestampSucceeded) {
            return saveTimestampRecord((Timestamper.TimestampSucceeded) result);
        } else if (result instanceof Timestamper.TimestampFailed) {
            Exception e = ((Timestamper.TimestampFailed) result).getCause();

            log.error("Timestamping failed", e);

            for (String tspUrl: ServerConf.getTspUrl()) {
                statusMap.put(tspUrl, new DiagnosticsStatus(DiagnosticsUtils.getErrorCode(e), LocalTime.now(), tspUrl));
            }

            throw e;
        } else {
            throw new RuntimeException("Unexpected result from Timestamper: " + result.getClass());
        }
    }

    private MessageRecord saveMessageRecord(SoapMessageImpl message, SignatureData signature, boolean clientSide)
            throws Exception {
        log.trace("saveMessageRecord()");

        return saveMessageRecord(createMessageRecord(message, signature, clientSide));
    }

    private static MessageRecord createMessageRecord(SoapMessageImpl message, SignatureData signature,
            boolean clientSide) throws Exception {
        log.trace("createMessageRecord()");

        String loggedMessage = new SoapMessageBodyManipulator().getLoggableMessageText(message, clientSide);

        MessageRecord messageRecord = new MessageRecord(message.getQueryId(), loggedMessage,
                signature.getSignatureXml(), message.isResponse(),
                clientSide ? message.getClient() : message.getService().getClientId());

        messageRecord.setTime(new Date().getTime());

        if (signature.isBatchSignature()) {
            messageRecord.setHashChainResult(signature.getHashChainResult());
            messageRecord.setHashChain(signature.getHashChain());
        }

        messageRecord.setSignatureHash(signatureHash(signature.getSignatureXml()));

        return messageRecord;
    }

    protected MessageRecord saveMessageRecord(MessageRecord messageRecord) throws Exception {
        LogRecordManager.saveMessageRecord(messageRecord);

        return messageRecord;
    }

    static TimestampRecord saveTimestampRecord(Timestamper.TimestampSucceeded message) throws Exception {
        log.trace("saveTimestampRecord()");

        statusMap.put(message.getUrl(), new DiagnosticsStatus(DiagnosticsErrorCodes.RETURN_SUCCESS, LocalTime.now()));

        TimestampRecord timestampRecord = createTimestampRecord(message);
        LogRecordManager.saveTimestampRecord(timestampRecord, message.getMessageRecords(), message.getHashChains());

        return timestampRecord;
    }

    private static TimestampRecord createTimestampRecord(Timestamper.TimestampSucceeded message) throws Exception {
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
        timestampFailed = null;
    }

    void setTimestampFailed(DateTime atTime) {
        if (timestampFailed == null) {
            timestampFailed = atTime;
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
                if (new DateTime().minusSeconds(period).isAfter(timestampFailed)) {
                    throw new CodedException(X_MLOG_TIMESTAMPER_FAILED, "Cannot time-stamp messages");
                }
            }
        }
    }

    private void registerCronJob(JobManager jobManager, String actorName, Object message, String cronExpression) {
        ActorSelection actor = getContext().actorSelection(actorName);
        JobDataMap jobData = MessageSendingJob.createJobData(actor, message);

        try {
            jobManager.registerJob(MessageSendingJob.class, actorName + "Job", cronExpression, jobData);
        } catch (SchedulerException e) {
            log.error("Unable to schedule job", e);
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
    public static class TimestamperJob extends UntypedActor {
        private static final int MIN_INTERVAL_SECONDS = 60;
        private static final int MAX_INTERVAL_SECONDS = 60 * 60 * 24;

        private FiniteDuration initialDelay;
        private Cancellable tick;

        public TimestamperJob(FiniteDuration initialDelay) {
            this.initialDelay = initialDelay;
        }

        @Override
        public void onReceive(Object message) throws Exception {
            log.trace("onReceive({})", message);

            if (START_TIMESTAMPING.equals(message)) {
                handle(message);
                schedule(getNextDelay());
            } else {
                unhandled(message);
            }
        }

        private void handle(Object message) {
            getContext().actorSelection("../" + TASK_QUEUE_NAME).tell(message, getSelf());
        }

        @Override
        public void preStart() throws Exception {
            schedule(initialDelay);
        }

        @Override
        public void postStop() {
            if (tick != null) {
                tick.cancel();
            }
        }

        private void schedule(FiniteDuration delay) {
            tick = getContext().system().scheduler().scheduleOnce(delay, getSelf(), START_TIMESTAMPING,
                    getContext().dispatcher(), ActorRef.noSender());
        }

        private FiniteDuration getNextDelay() {
            int actualInterval = MIN_INTERVAL_SECONDS;

            try {
                actualInterval = GlobalConf.getTimestampingIntervalSeconds();
            } catch (Exception e) {
                log.error("Failed to get timestamping interval", e);
            }

            int intervalSeconds = Math.min(Math.max(actualInterval, MIN_INTERVAL_SECONDS), MAX_INTERVAL_SECONDS);

            return Duration.create(intervalSeconds, TimeUnit.SECONDS);
        }
    }
}
