package ee.ria.xroad.proxy.messagelog;

import java.io.File;
import java.io.FileFilter;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

import akka.actor.Props;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.criterion.Restrictions;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ExpectedCodedException;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.messagelog.AbstractLogManager;
import ee.ria.xroad.common.messagelog.AbstractLogRecord;
import ee.ria.xroad.common.messagelog.LogRecord;
import ee.ria.xroad.common.messagelog.MessageLogProperties;
import ee.ria.xroad.common.messagelog.MessageRecord;
import ee.ria.xroad.common.messagelog.TimestampRecord;
import ee.ria.xroad.common.messagelog.archive.DigestEntry;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.util.JobManager;
import ee.ria.xroad.proxy.messagelog.Timestamper.TimestampFailed;
import ee.ria.xroad.proxy.messagelog.Timestamper.TimestampSucceeded;

import static ee.ria.xroad.common.ErrorCodes.X_MLOG_TIMESTAMPER_FAILED;
import static ee.ria.xroad.proxy.messagelog.MessageLogDatabaseCtx.doInTransaction;
import static ee.ria.xroad.proxy.messagelog.TestUtil.*;
import static org.junit.Assert.*;

/**
 * Contains tests to verify correct message log behavior.
 */
@Slf4j
public class MessageLogTest extends AbstractMessageLogTest {
    public static final String LAST_LOG_ARCHIVE_FILE =
            "mlog-20150520112233-20150520123344-asdlfjlasa.zip";

    public static final String LAST_DIGEST = "123567890abcdef";
    protected static Date logRecordTime;
    protected static Exception throwWhenSavingTimestamp;

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    /**
     * Logs a message and timestamps it explicitly.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void timestampingForced() throws Exception {
        initLogManager();

        log("02-04-2014 12:34:56.100", createMessage("forced"));
        assertTaskQueueSize(1);

        MessageRecord record = (MessageRecord) findByQueryId("forced",
                "02-04-2014 12:34:50.100", "02-04-2014 12:34:59.100");
        assertMessageRecord(record, "forced");

        TimestampRecord timestamp = timestamp(record);
        assertNotNull(timestamp);

        record = (MessageRecord) findByQueryId("forced",
                "02-04-2014 12:34:50.100", "02-04-2014 12:34:59.100");

        assertEquals(timestamp, record.getTimestampRecord());
        assertTaskQueueSize(0);
    }

    /**
     * Logs a message and calls explicit timestamping on it twice.
     * The returned timestamps must match.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void timestampingDouble() throws Exception {
        initLogManager();

        log("02-04-2014 12:34:56.100", createMessage("forced"));
        assertTaskQueueSize(1);

        MessageRecord record = (MessageRecord) findByQueryId("forced",
                "02-04-2014 12:34:50.100", "02-04-2014 12:34:59.100");
        assertMessageRecord(record, "forced");

        TimestampRecord timestamp1 = timestamp(record);
        assertNotNull(timestamp1);

        TimestampRecord timestamp2 = timestamp(record);
        assertNotNull(timestamp2);

        assertEquals(timestamp1, timestamp2);
    }

    /**
     * Logs 3 messages (message and signature is same) and time-stamps them.
     * Expects 1 time-stamp record and 3 message records that refer to
     * the time-stamp record. The time-stamp record must have hash chains.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void logThreeMessagesAndTimestamp() throws Exception {
        initLogManager();

        log(createMessage(), createSignature());
        log(createMessage(), createSignature());
        log(createMessage(), createSignature());
        assertTaskQueueSize(3);

        startTimestamping();

        TimestampSucceeded timestamp = waitForTimestampSuccessful();

        assertEquals(3, timestamp.getMessageRecords().length);
        assertNotNull(timestamp.getTimestampDer());
        assertNotNull(timestamp.getHashChainResult());
        assertEquals(3, timestamp.getHashChains().length);
        assertTaskQueueSize(0);
    }

    /**
     * Timestamps message immediately. No messages are expected to be in the
     * task queue.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void timestampImmediately() throws Exception {
        System.setProperty(MessageLogProperties.TIMESTAMP_IMMEDIATELY, "true");
        initLogManager();

        log(createMessage(), createSignature());
        assertTaskQueueSize(0);
    }

    /**
     * Timestamps message immediately, but time-stamping fails.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void timestampImmediatelyFail() throws Exception {
        System.setProperty(MessageLogProperties.TIMESTAMP_IMMEDIATELY, "true");
        initLogManager();

        TestTimestamperWorker.failNextTimestamping(true);

        try {
            log(createMessage(), createSignature());
            fail("Should fail to timestamp immediately");
        } catch (Exception expected) {
            log.info("Expected exception: " + expected);
        }
    }

    /**
     * Logs messages, time-stamps them. Then archives the messages and cleans
     * the database.
     * @throws Exception in case of any unexpected errors
     *
     * FUTURE As this test is quite expensive in terms of time and usable
     * resources (in addition depends on external utilities), consider moving
     * this test apart from unit tests.
     */
    @Test
    public void logTimestampArchiveAndClean() throws Exception {
        System.setProperty(MessageLogProperties.KEEP_RECORDS_FOR, "0");

        initLogManager();

        assertTaskQueueSize(0);
        log(createMessage(), createSignature());
        log(createMessage(), createSignature());
        log(createMessage(), createSignature());
        assertTaskQueueSize(3);

        startTimestamping();
        waitForTimestampSuccessful();
        assertTaskQueueSize(0);

        startArchiving();
        TestLogArchiver.waitForArchiveSuccessful();

        assertEquals(4, getNumberOfRecords(true));

        startCleaning();
        TestLogCleaner.waitForCleanSuccessful();
        assertEquals(0, getNumberOfRecords(true));

        assertArchiveHashChain();
    }

    /**
     * Logs 3 messages, time-stamping fails. Task queue must have 3 tasks.
     * Logs 1 more message, task queue must have 4 tasks.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void timestampingFailed() throws Exception {
        initLogManager();

        TestTimestamperWorker.failNextTimestamping(true);

        log(createMessage(), createSignature());
        log(createMessage(), createSignature());
        log(createMessage(), createSignature());
        assertTaskQueueSize(3);

        startTimestamping();

        Object result = waitForMessageInTaskQueue();
        assertTrue("Got " + result, result instanceof TimestampFailed);

        log(createMessage(), createSignature());
        assertTaskQueueSize(4);
    }

    /**
     * Logs messages, time-stamping failed. After acceptable period
     * no more messages are accepted.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void timestampingFailedStopLogging() throws Exception {
        initLogManager();

        thrown.expectError(X_MLOG_TIMESTAMPER_FAILED);

        System.setProperty(
                MessageLogProperties.ACCEPTABLE_TIMESTAMP_FAILURE_PERIOD, "1");
        TestTimestamperWorker.failNextTimestamping(true);

        log(createMessage(), createSignature());
        log(createMessage(), createSignature());
        log(createMessage(), createSignature());
        assertTaskQueueSize(3);

        DateTime atTime = new DateTime().minusMinutes(1);
        logManager.setTimestampFailed(atTime);

        startTimestamping();
        waitForMessageInTaskQueue();

        log(createMessage(), createSignature());
    }

    /**
     * Saving timestamp to database fails.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void failedToSaveTimestampToDatabase() throws Exception {
        throwWhenSavingTimestamp = new CodedException("expected");

        log(createMessage(), createSignature());
        log(createMessage(), createSignature());
        log(createMessage(), createSignature());
        assertTaskQueueSize(3);

        startTimestamping();

        waitForTimestampSuccessful();

        assertTrue(logManager.isTimestampFailed());

        log(createMessage(), createSignature());
        assertTaskQueueSize(4);
    }

    /**
     * Get message by query id.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void findByQueryId() throws Exception {
        initLogManager();

        log("02-04-2014 12:34:56.100", createMessage("message1"));
        log("02-04-2014 12:34:57.100", createMessage("message2"));
        log("02-04-2014 12:34:58.100", createMessage("message3"));

        LogRecord message1 = findByQueryId("message1",
                "02-04-2014 12:34:50.100", "02-04-2014 12:34:59.100");
        assertMessageRecord(message1, "message1");

        LogRecord message2 = findByQueryId("message2",
                "02-04-2014 12:34:50.100", "02-04-2014 12:34:59.100");
        assertMessageRecord(message2, "message2");

        LogRecord message3 = findByQueryId("message3",
                "02-04-2014 12:34:50.100", "02-04-2014 12:34:59.100");
        assertMessageRecord(message3, "message3");

        assertNull(findByQueryId("message1",
                "02-04-2014 12:34:56.200", "02-04-2014 12:34:59.100"));
        assertNull(findByQueryId("foo",
                "02-04-2014 12:34:56.100", "02-04-2014 12:34:59.100"));
    }

    /**
     * Wants to time-stamp, but no TSP urls configured.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void timestampNoTspUrls() throws Exception {
        ServerConf.reload(new EmptyServerConf());
        initLogManager();

        thrown.expectError(X_MLOG_TIMESTAMPER_FAILED);

        log(createMessage(), createSignature());
    }

    // ------------------------------------------------------------------------

    /**
     * Set up configuration.
     * @throws Exception in case of any unexpected errors
     */
    @Before
    public void setUp() throws Exception {
        // we do manual time-stamping
        System.setProperty(MessageLogProperties.TIMESTAMP_IMMEDIATELY, "false");
        System.setProperty(
                MessageLogProperties.ACCEPTABLE_TIMESTAMP_FAILURE_PERIOD,
                "1800");
        System.setProperty(MessageLogProperties.ARCHIVE_INTERVAL,
                "0 0 0/12 1/1 * ? *");
        System.setProperty(MessageLogProperties.CLEAN_INTERVAL,
                "0 0 0/12 1/1 * ? *");

        System.setProperty(MessageLogProperties.ARCHIVE_PATH, "build/");

        initForTest();
        testSetUp();
        initLastHashStep();

        logRecordTime = null;
        throwWhenSavingTimestamp = null;

        TestTimestamperWorker.failNextTimestamping(false);
    }

    @SneakyThrows
    private void initLastHashStep() {
        DigestEntry lastArchive =
                new DigestEntry(LAST_DIGEST, LAST_LOG_ARCHIVE_FILE);

        doInTransaction(session -> {
            session.createQuery(getLastEntryDeleteQuery()).executeUpdate();
            session.save(lastArchive);

            return null;
        });
    }

    /**
     * Cleanup test environment for other tests.
     * @throws Exception in case of any unexpected errors
     */
    @After
    public void tearDown() throws Exception {
        testTearDown();
        cleanUpDatabase();
    }

    @Override
    protected Class<? extends AbstractLogManager> getLogManagerImpl()
            throws Exception {
        return TestLogManager.class;
    }

    protected void log(String atDate, SoapMessageImpl message)
            throws Exception {
        log(atDate, message, createSignature());
    }

    protected void log(String atDate, SoapMessageImpl message,
            SignatureData signature) throws Exception {
        logRecordTime = getDate(atDate);
        log(message, signature);
    }

    protected LogRecord findByQueryId(String queryId, String startTime,
            String endTime) throws Exception {
        return logManager.findByQueryId(queryId, getDate(startTime),
                getDate(endTime));
    }


    private String getLastEntryDeleteQuery() {
        return "delete from " + DigestEntry.class.getName();
    }

    @SneakyThrows
    private void assertArchiveHashChain() {
        String archiveFilePath = getArchiveFilePath();

        String scriptFile = "../../doc/archive-hashchain-verifier.rb";
        String command = String.format("%s %s %s",
                scriptFile, archiveFilePath, LAST_DIGEST);

        ShellCommandOutput commandOutput = TestUtil.runShellCommand(command);

        if (commandOutput.isError()) {
            String errorMsg = String.format(
                    "Running hash chain verifying script failed on zip file "
                    + "'%s', script standard error:\n\t%s",
                    archiveFilePath,
                    commandOutput.getStandardError());

            throw new RuntimeException(errorMsg);
        }

        String lastHashStepInArchive = commandOutput.getStandardOutput().trim();
        String lastStepInDatabase = getLastHashStepInDatabase();

        if (!StringUtils.equals(lastStepInDatabase, lastHashStepInArchive)) {
            String message = String.format(
                    "Last hash step file must start with last hash step result, "
                    + "but does not. Result:\n\t%s", lastHashStepInArchive);

            throw new RuntimeException(message);
        }
    }

    @SneakyThrows
    private static String getLastHashStepInDatabase() {
        return doInTransaction(session -> {
            return (String) session
                    .createQuery(getLastDigestQuery())
                    .setMaxResults(1)
                    .list()
                    .get(0);
        });
    }

    private static String getLastDigestQuery() {
        return "select new java.lang.String(d.digest) from DigestEntry d "
                + "where d.digest is not null";
    }

    @SneakyThrows
    private String getArchiveFilePath() {
        File outputDir = new File("build");

        FileFilter fileFilter =
                new RegexFileFilter("^mlog-\\d+-\\d+-.\\w+\\.zip$");

        File[] files = outputDir.listFiles(fileFilter);

        File latestModifiedZip = null;

        for (File eachFile : files) {
            if (changesLatestModified(latestModifiedZip, eachFile)) {
                latestModifiedZip = eachFile;
            }
        }

        if (latestModifiedZip == null) {
            throw new RuntimeException("No archive files were created.");
        }

        return latestModifiedZip.getPath();
    }

    private static boolean changesLatestModified(File former, File candidate) {
        return former == null
                || former.lastModified() < candidate.lastModified();
    }

    private static Object waitForMessageInTaskQueue() throws Exception {
        TestTaskQueue.waitForMessage();

        Object message = TestTaskQueue.getLastMessage();
        assertNotNull("Did not get message from task queue", message);
        return message;
    }

    private static TimestampSucceeded waitForTimestampSuccessful()
            throws Exception {
        Object result = waitForMessageInTaskQueue();
        assertTrue("Got " + result, result instanceof TimestampSucceeded);

        return (TimestampSucceeded) result;
    }

    private static Date getDate(String dateStr) throws Exception {
        return new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS").parse(dateStr);
    }

    private static int getNumberOfRecords(final boolean archived)
            throws Exception {
        return doInTransaction(session -> {
            return session
                    .createCriteria(AbstractLogRecord.class)
                    .add(Restrictions.eq("archived", archived))
                    .list()
                    .size();
        });
    }

    private static class TestLogManager extends LogManager {

        TestLogManager(JobManager jobManager) throws Exception {
            super(jobManager);
        }

        @Override
        protected Props getTaskQueueImpl() {
            return Props.create(TestTaskQueue.class, this);
        }

        @Override
        protected Props getTimestamperImpl() {
            return Props.create(TestTimestamper.class);
        }

        @Override
        protected Props getArchiverImpl() {
            return Props.create(
                TestLogArchiver.class,
                Paths.get("build"),
                Paths.get("build/tmp")
            );
        }

        @Override
        protected Props getCleanerImpl() {
            return Props.create(TestLogCleaner.class);
        }

        @Override
        protected MessageRecord saveMessageRecord(MessageRecord messageRecord)
                throws Exception {
            if (logRecordTime != null) {
                messageRecord.setTime(logRecordTime.getTime());
            }

            return super.saveMessageRecord(messageRecord);
        }

        @Override
        protected TimestampRecord saveTimestampRecord(
                TimestampSucceeded message) throws Exception {
            if (throwWhenSavingTimestamp != null) {
                throw throwWhenSavingTimestamp;
            }

            return super.saveTimestampRecord(message);
        }
    }
}
