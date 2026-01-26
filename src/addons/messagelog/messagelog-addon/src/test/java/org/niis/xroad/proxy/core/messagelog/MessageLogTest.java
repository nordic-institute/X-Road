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
package org.niis.xroad.proxy.core.messagelog;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ExpectedCodedException;
import ee.ria.xroad.common.asic.AsicContainer;
import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.messagelog.AbstractLogManager;
import ee.ria.xroad.common.messagelog.LogRecord;
import ee.ria.xroad.common.messagelog.MessageLogProperties;
import ee.ria.xroad.common.messagelog.MessageRecord;
import ee.ria.xroad.common.messagelog.RestLogMessage;
import ee.ria.xroad.common.messagelog.TimestampRecord;
import ee.ria.xroad.common.messagelog.archive.GroupingStrategy;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.util.CacheInputStream;
import ee.ria.xroad.common.util.EncoderUtils;
import ee.ria.xroad.common.util.TimeUtils;
import ee.ria.xroad.messagelog.database.MessageRecordEncryption;
import ee.ria.xroad.messagelog.database.entity.AbstractLogRecordEntity;
import ee.ria.xroad.messagelog.database.entity.ArchiveDigestEntity;
import ee.ria.xroad.messagelog.database.entity.DigestEntryEmbeddable;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.assertj.core.api.Assertions;
import org.hibernate.Hibernate;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.proxy.core.messagelog.Timestamper.TimestampFailed;
import org.niis.xroad.proxy.core.messagelog.Timestamper.TimestampSucceeded;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.niis.xroad.proxy.core.messagelog.MessageLogDatabaseCtx.doInTransaction;
import static org.niis.xroad.proxy.core.messagelog.TestUtil.assertTaskQueueSize;
import static org.niis.xroad.proxy.core.messagelog.TestUtil.cleanUpDatabase;
import static org.niis.xroad.proxy.core.messagelog.TestUtil.createMessage;
import static org.niis.xroad.proxy.core.messagelog.TestUtil.createRestRequest;
import static org.niis.xroad.proxy.core.messagelog.TestUtil.createSignature;
import static org.niis.xroad.proxy.core.messagelog.TestUtil.initForTest;

/**
 * Contains tests to verify correct message log behavior.
 */
@Slf4j
@RunWith(Parameterized.class)
public class MessageLogTest extends AbstractMessageLogTest {
    private static final String LAST_LOG_ARCHIVE_FILE = "mlog-20150520112233-20150520123344-asdlfjlasa.zip";
    private static final String LAST_DIGEST = "123567890abcdef";

    @Parameterized.Parameters(name = "encrypted = {0}")
    public static Object[] params() {
        return new Object[]{Boolean.FALSE, Boolean.TRUE};
    }

    @Parameterized.Parameter()
    public boolean encrypted;

    static Date logRecordTime;

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    /**
     * Logs a message and timestamps it explicitly.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void timestampingForced() throws Exception {
        log.trace("timestampingForced()");

        log("02-04-2014 12:34:56.100", createMessage("forced"));
        assertTaskQueueSize(1);

        MessageRecord record = findByQueryId("forced");
        assertMessageRecord(record, "forced");

        TimestampRecord timestamp = timestamp(record);
        assertNotNull(timestamp);

        record = findByQueryId("forced");

        assertEquals(timestamp, record.getTimestampRecord());
        assertTaskQueueSize(0);
    }

    /**
     * Logs a message and calls explicit timestamping on it twice. The returned timestamps must match.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void timestampingDouble() throws Exception {
        log.trace("timestampingDouble()");

        log("02-04-2014 12:34:56.100", createMessage("forced"));
        assertTaskQueueSize(1);

        MessageRecord record = findByQueryId("forced");
        assertMessageRecord(record, "forced");

        TimestampRecord timestamp1 = timestamp(record);
        assertNotNull(timestamp1);

        TimestampRecord timestamp2 = timestamp(record);
        assertNotNull(timestamp2);

        assertEquals(timestamp1, timestamp2);
    }

    /**
     * Logs 3 messages (message and signature is same) and time-stamps them. Expects 1 time-stamp record and 3 message
     * records that refer to the time-stamp record. The time-stamp record must have hash chains.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void logThreeMessagesAndTimestamp() throws Exception {
        log.trace("logThreeMessagesAndTimestamp())");

        log(createMessage(), createSignature());
        log(createMessage(), createSignature());
        log(createMessage(), createSignature());

        assertTaskQueueSize(3);

        startTimestamping();

        TimestampSucceeded timestamp = waitForTimestampSuccessful();
        assertTrue(TestTaskQueue.waitForTimestampSaved());

        assertEquals(3, timestamp.getMessageRecords().length);
        assertNotNull(timestamp.getTimestampDer());
        assertNotNull(timestamp.getHashChainResult());
        assertEquals(3, timestamp.getHashChains().length);

        assertTaskQueueSize(0);
    }

    /**
     * Log message
     */
    @Test
    @SuppressWarnings("squid:S2699")
    public void logRestMessage() throws Exception {
        final String requestId = UUID.randomUUID().toString();
        final RestRequest message = createRestRequest("q-" + requestId, requestId);

        final Instant atDate = TimeUtils.now();
        final byte[] body = "\"test message body\"".getBytes(StandardCharsets.UTF_8);
        log(atDate, message, createSignature(), body);
        final MessageRecord logRecord = findByQueryId(message.getQueryId(), ClientId.Conf.create("XRD", "Class", "Member", "SubSystem"));

        MessageRecordEncryption.getInstance().prepareDecryption(logRecord);
        assertEquals(logRecord.getXRequestId(), requestId);
        assertEquals(logRecord.getQueryId(), message.getQueryId());
        final AsicContainer asic = logRecord.toAsicContainer();
        assertArrayEquals(asic.getMessage().getBytes(StandardCharsets.UTF_8), message.getMessageBytes());
        final byte[] attachment = IOUtils.readFully(asic.getAttachments().getFirst(), body.length);
        assertArrayEquals(body, attachment);
    }

    @Test
    public void logSoapWithAttachments() throws Exception {
        final String requestId = UUID.randomUUID().toString();
        final var message = createMessage(requestId);
        var attachment1 = "ONE".getBytes(StandardCharsets.UTF_8);
        var attachment2 = "TWO".getBytes(StandardCharsets.UTF_8);

        log(message, createSignature(), List.of(attachment1, attachment2));

        final MessageRecord logRecord = findByQueryId(message.getQueryId());
        MessageRecordEncryption.getInstance().prepareDecryption(logRecord);
        assertEquals(logRecord.getXRequestId(), requestId);
        assertEquals(logRecord.getQueryId(), message.getQueryId());

        final AsicContainer asic = logRecord.toAsicContainer();
        assertEquals(asic.getMessage(), message.getXml());
        var attachments = asic.getAttachments().stream().map(MessageLogTest::readAllBytes).toList();
        Assertions.assertThat(attachments).containsExactly(attachment1, attachment2);
    }

    /**
     * Test for system property timestamp-records-limit
     */
    @Test
    public void testTimestampRecordsLimit() throws Exception {
        log.trace("testTimestampRecordsLimit()");
        int orig = MessageLogProperties.getTimestampRecordsLimit();
        try {
            TestTaskQueue.successfulMessageSizes.clear();
            System.setProperty(MessageLogProperties.TIMESTAMP_RECORDS_LIMIT, "2");
            log(createMessage(), createSignature());
            log(createMessage(), createSignature());
            log(createMessage(), createSignature());
            log(createMessage(), createSignature());
            log(createMessage(), createSignature());
            assertTaskQueueSize(5);

            startTimestamping();

            assertEquals(List.of(2, 2, 1), TestTaskQueue.successfulMessageSizes);
        } finally {
            System.setProperty(MessageLogProperties.TIMESTAMP_RECORDS_LIMIT, String.valueOf(orig));
        }
    }

    /**
     * Timestamps message immediately. No messages are expected to be in the task queue.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void timestampImmediately() throws Exception {
        log.trace("timestampImmediately()");

        System.setProperty(MessageLogProperties.TIMESTAMP_IMMEDIATELY, "true");

        log(createMessage(), createSignature());
        assertTaskQueueSize(0);
    }

    /**
     * Timestamps message immediately, but time-stamping fails.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void timestampImmediatelyFail() throws Exception {
        log.trace("timestampImmediatelyFail()");

        System.setProperty(MessageLogProperties.TIMESTAMP_IMMEDIATELY, "true");

        TestTimestamperWorker.failNextTimestamping(true);

        SoapMessageImpl message = createMessage();
        SignatureData signature = createSignature();
        try {
            log(message, signature);
            fail("Should fail to timestamp immediately");
        } catch (Exception expected) {
            log.info("Expected exception: " + expected);
        }
    }

    /**
     * Logs messages, time-stamps them. Then archives the messages and cleans the database.
     *
     * @throws Exception in case of any unexpected errors
     *                   <p>
     *                   FUTURE As this test is quite expensive in terms of time and usable resources (in addition
     *                   depends on external
     *                   utilities), consider moving this test apart from unit tests.
     */
    @Test
    public void logTimestampArchiveAndClean() throws Exception {
        log.trace("logTimestampArchiveAndClean()");

        System.setProperty(MessageLogProperties.KEEP_RECORDS_FOR, "0");

        assertTaskQueueSize(0);
        log("01-09-2021 12:34:55.100", createMessage(), createSignature());
        log("01-09-2021 12:34:57.200", createMessage(), createSignature());
        log("01-09-2021 12:34:59.300", createMessage(), createSignature());
        assertTaskQueueSize(3);

        startTimestamping();
        waitForTimestampSuccessful();
        assertTrue(TestTaskQueue.waitForTimestampSaved());

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
     * Logs 3 messages, time-stamping fails. Task queue must have 3 tasks. Logs 1 more message, task queue must
     * have 4 tasks.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void timestampingFailed() throws Exception {
        log.trace("timestampingFailed()");

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
     * Logs messages, time-stamping failed. After acceptable period no more messages are accepted.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void timestampingFailedStopLogging() throws Exception {
        log.trace("timestampingFailedStopLogging()");

        thrown.expectError(ErrorCode.TIMESTAMPING_FAILED.code());

        System.setProperty(MessageLogProperties.ACCEPTABLE_TIMESTAMP_FAILURE_PERIOD, "1");
        TestTimestamperWorker.failNextTimestamping(true);

        log(createMessage(), createSignature());
        log(createMessage(), createSignature());
        log(createMessage(), createSignature());
        assertTaskQueueSize(3);

        logManager.setTimestampFailed(TimeUtils.now().minusSeconds(60));

        startTimestamping();
        waitForMessageInTaskQueue();

        log(createMessage(), createSignature());
    }

    /**
     * Saving timestamp to database fails.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void failedToSaveTimestampToDatabase() throws Exception {
        log.trace("failedToSaveTimestampToDatabase()");

        TestTaskQueue.throwWhenSavingTimestamp = new CodedException("expected");

        log(createMessage(), createSignature());
        log(createMessage(), createSignature());
        log(createMessage(), createSignature());

        assertTaskQueueSize(3);

        log.info("startTimestamping();");

        startTimestamping();

        log.info("waitForTimestampSuccessful();");

        waitForTimestampSuccessful();

        log.info("TestTaskQueue.waitForTimestampSaved();");

        assertTrue(TestTaskQueue.waitForTimestampSaved());

        log.info("TestLogManager.waitForSetTimestampingStatus()");

        assertTrue(TestLogManager.waitForSetTimestampingStatus());

        assertTrue(logManager.isTimestampFailed());

        log(createMessage(), createSignature());
        assertTaskQueueSize(4);
    }

    /**
     * Get message by query id.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void findByQueryId() throws Exception {
        log.trace("findByQueryId()");

        log("02-04-2014 12:34:56.100", createMessage("message1"));
        log("02-04-2014 12:34:57.100", createMessage("message2"));
        log("02-04-2014 12:34:58.100", createMessage("message3"));

        LogRecord message1 = findByQueryId("message1");
        assertMessageRecord(message1, "message1");

        LogRecord message2 = findByQueryId("message2");
        assertMessageRecord(message2, "message2");

        LogRecord message3 = findByQueryId("message3");
        assertMessageRecord(message3, "message3");

        assertNull(findByQueryId("foo"));
    }

    /**
     * Wants to time-stamp, but no TSP urls configured.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void timestampNoTspUrls() throws Exception {
        log.trace("timestampNoTspUrls()");

        serverConfProvider.setServerConfProvider(new EmptyServerConf());
        thrown.expectError(ErrorCode.NO_TIMESTAMPING_PROVIDER_FOUND.code());

        log(createMessage(), createSignature());
    }



    // ------------------------------------------------------------------------

    /**
     * Set up configuration.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Before
    public void setUp() throws Exception {
        // we do manual time-stamping
        System.setProperty(MessageLogProperties.TIMESTAMP_IMMEDIATELY, "false");
        System.setProperty(MessageLogProperties.ACCEPTABLE_TIMESTAMP_FAILURE_PERIOD, "1800");
        System.setProperty(MessageLogProperties.ARCHIVE_INTERVAL, "0 0 0 1 1 ? 2099");
        System.setProperty(MessageLogProperties.CLEAN_INTERVAL, "0 0 0 1 1 ? 2099");

        System.setProperty(MessageLogProperties.ARCHIVE_PATH, "build/");
        System.setProperty(MessageLogProperties.ARCHIVE_GROUPING, GroupingStrategy.SUBSYSTEM.name());

        System.setProperty(MessageLogProperties.MESSAGELOG_ENCRYPTION_ENABLED, Boolean.valueOf(encrypted).toString());
        System.setProperty(MessageLogProperties.MESSAGELOG_KEYSTORE_PASSWORD, "password");
        System.setProperty(MessageLogProperties.MESSAGELOG_KEYSTORE, "build/resources/test/messagelog.p12");
        System.setProperty(MessageLogProperties.MESSAGELOG_KEY_ID, "key1");

        initForTest();
        testSetUp();
        initLastHashStep();

        // initialize states
        initLogManager();
        TestLogManager.initSetTimestampingStatusLatch();
        TestTaskQueue.initGateLatch();
        TestTaskQueue.initTimestampSavedLatch();

        logRecordTime = null;
        TestTaskQueue.throwWhenSavingTimestamp = null;

        TestTimestamperWorker.failNextTimestamping(false);
    }

    private void initLastHashStep() throws Exception {
        var lastArchive = new DigestEntryEmbeddable();
        lastArchive.setDigest(LAST_DIGEST);
        lastArchive.setFileName(LAST_LOG_ARCHIVE_FILE);
        var digest = new ArchiveDigestEntity();
        digest.setGroupName(ClientId.Conf.create("XRD", "BUSINESS", "consumer").toShortString());
        digest.setDigestEntry(lastArchive);
        doInTransaction(session -> {
            session.createMutationQuery(getLastEntryDeleteQuery()).executeUpdate();
            session.persist(digest);
            return null;
        });
    }

    /**
     * Cleanup test environment for other tests.
     *
     * @throws Exception in case of any unexpected errors
     */
    @After
    public void tearDown() throws Exception {
        System.clearProperty(MessageLogProperties.MESSAGELOG_ENCRYPTION_ENABLED);
        System.clearProperty(MessageLogProperties.MESSAGELOG_KEYSTORE_PASSWORD);
        System.clearProperty(MessageLogProperties.MESSAGELOG_KEYSTORE);
        System.clearProperty(MessageLogProperties.MESSAGELOG_KEY_ID);
        System.clearProperty(MessageLogProperties.ARCHIVE_ENCRYPTION_ENABLED);

        testTearDown();
        cleanUpDatabase();
    }

    @Override
    protected Class<? extends AbstractLogManager> getLogManagerImpl() {
        return TestLogManager.class;
    }

    protected void log(String atDate, SoapMessageImpl message) throws Exception {
        log(atDate, message, createSignature());
    }

    protected void log(String atDate, SoapMessageImpl message, SignatureData signature) throws Exception {
        logRecordTime = getDate(atDate);
        log(message, signature);
    }

    protected void log(Instant instant, RestRequest message, SignatureData signatureData, byte[] body)
            throws Exception {
        final ByteArrayInputStream bos = new ByteArrayInputStream(body);
        final CacheInputStream cis = new CacheInputStream(bos, bos.available());

        logRecordTime = Date.from(instant);

        final RestLogMessage logMessage = new RestLogMessage(message.getQueryId(),
                message.getClientId(),
                message.getServiceId(),
                message, signatureData,
                cis,
                true,
                message.getXRequestId());
        logManager.log(logMessage);
    }

    protected MessageRecord findByQueryId(String queryId) throws Exception {
        ClientId clientId = ClientId.Conf.create("EE", "BUSINESS", "consumer");
        return LogRecordManager.getByQueryIdUnique(queryId, clientId, false, MessageLogTest::initializeAttachments);
    }

    protected MessageRecord findByQueryId(String queryId, ClientId clientId) throws Exception {
        return LogRecordManager.getByQueryIdUnique(queryId, clientId, false, MessageLogTest::initializeAttachments);
    }

    private String getLastEntryDeleteQuery() {
        return "delete from " + ArchiveDigestEntity.class.getName();
    }

    private void assertArchiveHashChain() throws Exception {
        String archiveFilePath = getArchiveFilePath();

        final MessageDigest digest = MessageDigest.getInstance(MessageLogProperties.getHashAlg().name());
        final Map<String, byte[]> digests = new HashMap<>();
        String linkinginfo = null;

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(Paths.get(archiveFilePath)))) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                digest.reset();
                final byte[] buf = new byte[4096];
                int len = 0;
                if ("linkinginfo".equals(ze.getName())) {
                    StringBuilder builder = new StringBuilder();
                    while ((len = zis.read(buf)) > 0) {
                        builder.append(new String(buf, 0, len, StandardCharsets.UTF_8));
                    }
                    linkinginfo = builder.toString();
                } else {
                    while ((len = zis.read(buf)) > 0) {
                        digest.update(buf, 0, len);
                    }
                    digests.put(ze.getName(), digest.digest());
                }
            }
        }

        String prevHash = null;
        for (final String line : linkinginfo.split("\n")) {
            final String[] parts = line.split("\\s+");
            if (prevHash == null) {
                prevHash = parts[0];
                assertEquals(LAST_DIGEST, prevHash);
                assertEquals(MessageLogProperties.getHashAlg(), DigestAlgorithm.ofName(parts[2]));
            } else {
                digest.reset();
                digest.update(prevHash.getBytes());
                final byte[] d = digests.get(parts[1]);
                assertNotNull("Archive did not contain file " + parts[1], d);
                digest.update(EncoderUtils.encodeHex(d).getBytes());
                prevHash = EncoderUtils.encodeHex(digest.digest());
                assertEquals("Digest does not match", parts[0], prevHash);
            }
        }

        String lastStepInDatabase = getLastHashStepInDatabase();

        assertEquals("Last hash step file must start with last hash step result", lastStepInDatabase, prevHash);
    }

    private static String getLastHashStepInDatabase() throws Exception {
        return doInTransaction(session -> session
                .createQuery(getLastDigestQuery(), String.class)
                .setMaxResults(1)
                .list()
                .getFirst());
    }

    private static String getLastDigestQuery() {
        return "select new java.lang.String(d.digestEntry.digest) "
                + "from ArchiveDigestEntity d where d.digestEntry.digest is not null";
    }

    private String getArchiveFilePath() {
        File outputDir = archivesPath.toFile();

        FileFilter fileFilter = new RegexFileFilter("^mlog.*-\\d+-\\d+-.\\w+\\.zip$");

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
        return former == null || former.lastModified() < candidate.lastModified();
    }

    private static Date getDate(String dateStr) throws Exception {
        return new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS").parse(dateStr);
    }

    private static int getNumberOfRecords(final boolean archived) throws Exception {
        return doInTransaction(session -> {
            final CriteriaBuilder cb = session.getCriteriaBuilder();
            final CriteriaQuery<Number> query = cb.createQuery(Number.class);
            final Root<AbstractLogRecordEntity> r = query.from(AbstractLogRecordEntity.class);
            query.select(cb.count(r)).where(cb.equal(r.get("archived"), archived));
            return session.createQuery(query).getSingleResult().intValue();
        });
    }

    private static MessageRecord initializeAttachments(MessageRecord messageRecord) {
        if (messageRecord != null) {
            Hibernate.initialize(messageRecord.getAttachments());
        }
        return messageRecord;
    }

    @SneakyThrows
    private static byte[] readAllBytes(InputStream is) {
        return is.readAllBytes();
    }

}
