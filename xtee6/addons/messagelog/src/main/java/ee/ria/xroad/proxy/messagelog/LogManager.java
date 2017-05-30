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

import akka.actor.*;
import akka.pattern.Patterns;
import akka.util.Timeout;
import ee.ria.xroad.common.*;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.messagelog.*;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.util.JobManager;
import ee.ria.xroad.common.util.MessageSendingJob;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.quartz.JobDataMap;
import org.quartz.SchedulerException;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.time.LocalTime;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static ee.ria.xroad.common.ErrorCodes.X_SLOG_TIMESTAMPER_FAILED;
import static ee.ria.xroad.common.messagelog.MessageLogProperties.*;
import static ee.ria.xroad.common.util.CryptoUtils.calculateDigest;
import static ee.ria.xroad.common.util.CryptoUtils.encodeBase64;
import static ee.ria.xroad.proxy.messagelog.LogArchiver.START_ARCHIVING;
import static ee.ria.xroad.proxy.messagelog.LogCleaner.START_CLEANING;
import static ee.ria.xroad.proxy.messagelog.TaskQueue.START_TIMESTAMPING;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Message log manager. Sets up the whole logging system components.
 * The logging system consists of a task queue, timestamper, archiver and
 * log cleaner.
 */
@Slf4j
public class LogManager extends AbstractLogManager {

    private static final Timeout TIMESTAMP_TIMEOUT =
            new Timeout(Duration.create(30, TimeUnit.SECONDS));

    // Actor names of secure log components
    static final String TASK_QUEUE_NAME = "RequestLogTaskQueue";
    static final String TIMESTAMPER_NAME = "RequestLogTimestamper";
    static final String ARCHIVER_NAME = "RequestLogArchiver";
    static final String CLEANER_NAME = "RequestLogCleaner";

    private final LogRecordManager logRecordManager = new LogRecordManager();



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
        taskQueueRef = getContext().actorOf(Props.create(getTaskQueueImpl()),
                TASK_QUEUE_NAME);
    }

    private void createTimestamper() {
        timestamper = getContext().actorOf(
                Props.create(getTimestamperImpl()), TIMESTAMPER_NAME);

        getContext().actorOf(Props.create(TimestamperJob.class, getTimestamperJobInitialDelay()));
    }

    /**
     * Can be overwritten in test classes if we want to make sure that timestamping
     * does not start prematurely
     * @return
     */
    protected FiniteDuration getTimestamperJobInitialDelay() {
        return Duration.create(1, TimeUnit.SECONDS);
    }

    private void createArchiver(JobManager jobManager) {
        getContext().actorOf(Props.create(getArchiverImpl()), ARCHIVER_NAME);

        registerCronJob(jobManager, ARCHIVER_NAME, START_ARCHIVING,
                getArchiveInterval());
    }

    private void createCleaner(JobManager jobManager) {
        getContext().actorOf(Props.create(getCleanerImpl()), CLEANER_NAME);

        registerCronJob(jobManager, CLEANER_NAME, START_CLEANING,
                getCleanInterval());
    }

    // ------------------------------------------------------------------------

    @Override
    protected void log(SoapMessageImpl message, SignatureData signature,
                       boolean clientSide) throws Exception {
        verifyCanLogMessage();

        MessageRecord logRecord = saveMessageRecord(message, signature,
                clientSide);

        if (shouldTimestampImmediately()) {
            timestampImmediately(logRecord);
        }
    }

    @Override
    protected TimestampRecord timestamp(Long messageRecordId) throws Exception {
        MessageRecord messageRecord =
                (MessageRecord) logRecordManager.get(messageRecordId);
        if (messageRecord.getTimestampRecord() != null) {
            return messageRecord.getTimestampRecord();
        } else {
            return timestampImmediately(messageRecord);
        }
    }

    @Override
    protected LogRecord findByQueryId(String queryId, Date startTime,
                                      Date endTime) throws Exception {
        return logRecordManager.getByQueryId(queryId, startTime, endTime);
    }

    @Override
    public void onReceive(Object message) throws Exception {
        try {
            if (message instanceof String && CommonMessages.TIMESTAMP_STATUS.equals(message)) {
                getSender().tell(statusMap, getSelf());
            } else if (message instanceof SetTimestampingStatusMessage) {
                setTimestampingStatus((SetTimestampingStatusMessage) message);
            } else if (message instanceof SaveTimestampedDataMessage) {
                SaveTimestampedDataMessage data = (SaveTimestampedDataMessage) message;
                saveTimestampRecord(data.getTimestampSucceeded());
            } else {
                super.onReceive(message);
            }
        } catch (Exception e) {
            getSender().tell(e, getSelf());
        }
    }

    // ------------------------------------------------------------------------

    protected Class<? extends TaskQueue> getTaskQueueImpl() {
        return TaskQueue.class;
    }

    protected Class<? extends Timestamper> getTimestamperImpl() {
        return Timestamper.class;
    }

    protected Class<? extends LogArchiver> getArchiverImpl() {
        return LogArchiver.class;
    }

    protected Class<? extends LogCleaner> getCleanerImpl() {
        return LogCleaner.class;
    }

    protected TimestampRecord timestampImmediately(MessageRecord logRecord)
            throws Exception {
        log.trace("timestampImmediately({})", logRecord);

        try {


            Object result = Await.result(Patterns.ask(timestamper,
                            new Timestamper.TimestampTask(logRecord), TIMESTAMP_TIMEOUT),
                    TIMESTAMP_TIMEOUT.duration());


            if (result instanceof Timestamper.TimestampSucceeded) {
                return saveTimestampRecord((Timestamper.TimestampSucceeded) result);
            } else if (result instanceof Timestamper.TimestampFailed) {
                Exception e = ((Timestamper.TimestampFailed) result).getCause();
                log.warn("Timestamp failed: {}", e);
                for (String tspUrl: ServerConf.getTspUrl()) {
                    statusMap.put(tspUrl, new DiagnosticsStatus(DiagnosticsUtils.getErrorCode(e), LocalTime.now(),
                            tspUrl));
                }
                throw e;
            } else {

                throw new RuntimeException(
                        "Unexpected result from Timestamper: " + result.getClass());
            }
        } catch (Exception e) {
            throw e;
        }
    }

    protected MessageRecord saveMessageRecord(SoapMessageImpl message,
                                              SignatureData signature, boolean clientSide) throws Exception {
        log.trace("saveMessageRecord()");

        String loggedMessage = new SoapMessageBodyManipulator().getLoggableMessageText(message, clientSide);

        MessageRecord messageRecord =
                new MessageRecord(message.getQueryId(),
                        loggedMessage,
                        signature.getSignatureXml(),
                        message.isResponse(),
                        clientSide ? message.getClient()
                                : message.getService().getClientId());

        messageRecord.setTime(new Date().getTime());

        if (signature.isBatchSignature()) {
            messageRecord.setHashChainResult(signature.getHashChainResult());
            messageRecord.setHashChain(signature.getHashChain());
        }

        messageRecord.setSignatureHash(
                signatureHash(signature.getSignatureXml()));

        return saveMessageRecord(messageRecord);
    }

    protected MessageRecord saveMessageRecord(MessageRecord messageRecord)
            throws Exception {
        logRecordManager.saveMessageRecord(messageRecord);
        return messageRecord;
    }

    /**
     * Only externally use this method from tests. Otherwise send message to this actor.
     * Calls "atomic" / synchronized method storeTimestampAndSetStatus, so that we can trust in setTimestampFailed
     * that task queue remains in same (empty / non-empty) state between checking and setting status.
     */
    private TimestampRecord saveTimestampRecord(
            Timestamper.TimestampSucceeded message) throws Exception {
        log.trace("saveTimestampRecord()");

        statusMap.put(message.getUrl(), new DiagnosticsStatus(DiagnosticsErrorCodes.RETURN_SUCCESS, LocalTime.now()));

        TimestampRecord timestampRecord = new TimestampRecord();
        timestampRecord.setTime(new Date().getTime());
        timestampRecord.setTimestamp(encodeBase64(message.getTimestampDer()));

        String hashChainResult = message.getHashChainResult() != null
                ? message.getHashChainResult() : null;
        timestampRecord.setHashChainResult(hashChainResult);

        storeTimestampAndSetStatus(message, timestampRecord);

        return timestampRecord;
    }

    /**
     * Stores timestamped records, and sets status to succeeded if everything went as expected.
     * This method is synchronized so that these two operations are executed atomically and do not disturb each other:
     * 1) storeTimestampAndSetStatus: stores timestamp records and sets status
     * 2) setTimestampFailed: reads status, checks existence of unstamped records and sets status
     * @param message
     * @param timestampRecord
     * @throws Exception
     */
    private synchronized void storeTimestampAndSetStatus(Timestamper.TimestampSucceeded message,
                                                         TimestampRecord timestampRecord) throws Exception {
        try {
            persistTimestampRecord(message, timestampRecord);
            setTimestampSucceeded();
        } catch (Exception e) {
            log.error("Failed to save time-stamp record to database", e);
            setTimestampFailedRegardlessOfQueue(new DateTime());
            throw e;
        }
    }

    /**
     * Extension point for the tests
     * @param message
     * @param timestampRecord
     * @throws Exception
     */
    protected void persistTimestampRecord(Timestamper.TimestampSucceeded message,
                                          TimestampRecord timestampRecord) throws Exception {
        logRecordManager.saveTimestampRecord(timestampRecord,
                message.getMessageRecords(), message.getHashChains());
    }

    boolean isTimestampFailed() {
        return timestampFailed != null;
    }

    private void setTimestampingStatus(SetTimestampingStatusMessage statusMessage) {
        if (statusMessage.getStatus() == SetTimestampingStatusMessage.Status.SUCCESS) {
            setTimestampSucceeded();
        } else {
            setTimestampFailedIfQueueIsEmpty(statusMessage.getAtTime());
        }
    }

    /**
     * Only externally use this method from tests. Otherwise send message to this actor.
     * @param atTime
     */
    void setTimestampFailedIfQueueIsEmpty(DateTime atTime) {
        setTimestampFailed(atTime, true);
    }

    /**
     * Override checking the task queue and set status to failed regardless
     * @param atTime
     */
    private void setTimestampFailedRegardlessOfQueue(DateTime atTime) {
        setTimestampFailed(atTime, false);
    }

    /**
     * This method is synchronized so that these two operations are executed atomically and do not disturb each other:
     * 1) setTimestampFailed: reads status, checks existence of unstamped records and sets status
     * 2) storeTimestampAndSetStatus: stores timestamp records and sets status
     * @param atTime
     * @param verifyQueueSize if we check against timestamping task queue being empty or not
     */
    private synchronized void setTimestampFailed(DateTime atTime, boolean verifyQueueSize) {
        if (timestampFailed == null) {
            if (verifyQueueSize) {
                // only change status to failed if there are some timestamping statuses in queue
                // otherwise it is likely that some other request has stamped these requests,
                // and changing status to failed with empty queue would result in a timestamping
                if (queueIsKnownToBeEmpty()) {
                    log.info("ignoring time stamping fail-status since there are no timestamping tasks in the queue "
                            + "(another time stamping task was successful)");
                    return;
                }
            }
            timestampFailed = atTime;
        }
    }

    /**
     * @return true if task queue is empty,
     * false if it is not empty OR we cannot determine the size
     */
    private boolean queueIsKnownToBeEmpty() {
        try {
            if (TaskQueue.isTimestampTasksEmpty()) {
                return true;
            }
        } catch (TaskQueue.CannotDetermineTaskQueueSize e) {
            log.error("Cannot determine task queue size", e);
        }
        return false;
    }

    /**
     * Only externally use this method from tests. Otherwise send message to this actor.
     */
    void setTimestampSucceeded() {
        timestampFailed = null;
    }

    void verifyCanLogMessage() {
        int period = getAcceptableTimestampFailurePeriodSeconds();
        if (period == 0) { // check disabled
            return;
        }

        if (ServerConf.getTspUrl().isEmpty()) {
            throw new CodedException(X_SLOG_TIMESTAMPER_FAILED,
                    "Cannot time-stamp messages: "
                            + "no timestamping services configured");
        }

        if (isTimestampFailed()) {
            if (new DateTime().minusSeconds(period).isAfter(timestampFailed)) {
                throw new CodedException(X_SLOG_TIMESTAMPER_FAILED,
                        "Cannot time-stamp messages");
            }
        }
    }

    void registerCronJob(JobManager jobManager, String actorName,
                         Object message, String cronExpression) {
        ActorSelection actor = getContext().actorSelection(actorName);

        JobDataMap jobData = MessageSendingJob.createJobData(actor, message);
        try {
            jobManager.registerJob(MessageSendingJob.class, actorName + "Job",
                    cronExpression, jobData);
        } catch (SchedulerException e) {
            log.error("Unable to schedule job", e);
        }
    }

    static String signatureHash(String signatureXml) throws Exception {
        return encodeBase64(getInputHash(signatureXml));
    }

    private static byte[] getInputHash(String str) throws Exception {
        String hashAlgoId = MessageLogProperties.getHashAlg();
        return calculateDigest(hashAlgoId, str.getBytes(UTF_8));
    }

    /**
     * Timestamper job is responsible for firing up the timestamping
     * periodically.
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
            if (START_TIMESTAMPING.equals(message)) {
                handle(message);
                schedule(getNextDelay());
            } else {
                unhandled(message);
            }
        }

        private void handle(Object message) {
            getContext().actorSelection("../" + TASK_QUEUE_NAME).tell(message,
                    getSelf());
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
            tick = getContext().system().scheduler().scheduleOnce(delay,
                    getSelf(), START_TIMESTAMPING, getContext().dispatcher(),
                    ActorRef.noSender());
        }

        private FiniteDuration getNextDelay() {
            int actualInterval = MIN_INTERVAL_SECONDS;
            try {
                actualInterval = GlobalConf.getTimestampingIntervalSeconds();
            } catch (Exception e) {
                log.error("Failed to get timestamping interval", e);
            }

            int intervalSeconds =
                    Math.min(Math.max(actualInterval,
                            MIN_INTERVAL_SECONDS), MAX_INTERVAL_SECONDS);

            return Duration.create(intervalSeconds, TimeUnit.SECONDS);
        }
    }
}
