package ee.ria.xroad.proxy.messagelog;

import java.nio.file.Paths;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.pattern.Patterns;
import akka.util.Timeout;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.quartz.JobDataMap;
import org.quartz.SchedulerException;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import ee.ria.xroad.common.CodedException;
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

    private void createTaskQueue() {
        getContext().actorOf(getTaskQueueImpl(), TASK_QUEUE_NAME);
    }

    private void createTimestamper() {
        timestamper = getContext().actorOf(
                getTimestamperImpl(),
                TIMESTAMPER_NAME
            );

        getContext().actorOf(Props.create(TimestamperJob.class));
    }

    private void createArchiver(JobManager jobManager) {
        getContext().actorOf(getArchiverImpl(), ARCHIVER_NAME);

        registerCronJob(jobManager, ARCHIVER_NAME, START_ARCHIVING,
                getArchiveInterval());
    }

    private void createCleaner(JobManager jobManager) {
        getContext().actorOf(getCleanerImpl(), CLEANER_NAME);

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

    // ------------------------------------------------------------------------

    protected Props getTaskQueueImpl() {
        return Props.create(TaskQueue.class, this);
    }

    protected Props getTimestamperImpl() {
        return Props.create(Timestamper.class);
    }

    protected Props getArchiverImpl() {
        return Props.create(
            LogArchiver.class,
            Paths.get(MessageLogProperties.getArchivePath()),
            Paths.get(SystemProperties.getTempFilesPath())
        );
    }

    protected Props getCleanerImpl() {
        return Props.create(LogCleaner.class);
    }

    protected TimestampRecord timestampImmediately(MessageRecord logRecord)
            throws Exception {
        log.trace("timestampImmediately({})", logRecord);

        Object result = Await.result(Patterns.ask(timestamper,
                new Timestamper.TimestampTask(logRecord), TIMESTAMP_TIMEOUT),
                TIMESTAMP_TIMEOUT.duration());

        if (result instanceof Timestamper.TimestampSucceeded) {
            return saveTimestampRecord((Timestamper.TimestampSucceeded) result);
        } else if (result instanceof Timestamper.TimestampFailed) {
            throw ((Timestamper.TimestampFailed) result).getCause();
        } else {
            throw new RuntimeException(
                    "Unexpected result from Timestamper: " + result.getClass());
        }
    }

    protected MessageRecord saveMessageRecord(SoapMessageImpl message,
            SignatureData signature, boolean clientSide) throws Exception {
        log.trace("saveMessageRecord()");

        MessageRecord messageRecord =
                new MessageRecord(message, signature.getSignatureXml(),
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

    protected TimestampRecord saveTimestampRecord(
            Timestamper.TimestampSucceeded message) throws Exception {
        log.trace("saveTimestampRecord()");

        TimestampRecord timestampRecord = new TimestampRecord();
        timestampRecord.setTime(new Date().getTime());
        timestampRecord.setTimestamp(encodeBase64(message.getTimestampDer()));

        String hashChainResult = message.getHashChainResult() != null
                ? message.getHashChainResult() : null;
        timestampRecord.setHashChainResult(hashChainResult);

        logRecordManager.saveTimestampRecord(timestampRecord,
                message.getMessageRecords(), message.getHashChains());

        return timestampRecord;
    }

    boolean isTimestampFailed() {
        return timestampFailed != null;
    }

    synchronized void setTimestampFailed(DateTime atTime) {
        if (timestampFailed == null) {
            timestampFailed = atTime;
        }
    }

    synchronized void setTimestampSucceeded() {
        timestampFailed = null;
    }

    synchronized void verifyCanLogMessage() {
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
        return calculateDigest(getHashAlg(), str.getBytes(UTF_8));
    }

    /**
     * Timestamper job is responsible for firing up the timestamping
     * periodically.
     */
    public static class TimestamperJob extends UntypedActor {

        private static final int MIN_INTERVAL_SECONDS = 60;
        private static final int MAX_INTERVAL_SECONDS = 60 * 60 * 24;

        private static final FiniteDuration INITIAL_DELAY =
                Duration.create(1, TimeUnit.SECONDS);

        private Cancellable tick;

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
            schedule(INITIAL_DELAY);
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
