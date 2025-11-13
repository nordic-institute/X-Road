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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.asic.AsicContainer;
import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.util.CacheInputStream;
import ee.ria.xroad.common.util.EncoderUtils;
import ee.ria.xroad.common.util.TimeUtils;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.assertj.core.api.Assertions;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.messagelog.LogRecord;
import org.niis.xroad.messagelog.MessageRecord;
import org.niis.xroad.messagelog.RestLogMessage;
import org.niis.xroad.messagelog.TimestampRecord;
import org.niis.xroad.messagelog.archive.GroupingStrategy;
import org.niis.xroad.messagelog.entity.AbstractLogRecordEntity;
import org.niis.xroad.messagelog.entity.ArchiveDigestEntity;
import org.niis.xroad.messagelog.entity.DigestEntryEmbeddable;
import org.niis.xroad.proxy.core.addon.messagelog.Timestamper.TimestampFailed;
import org.niis.xroad.proxy.core.addon.messagelog.Timestamper.TimestampSucceeded;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.niis.xroad.proxy.core.addon.messagelog.ProxyTestUtil.assertTaskQueueSize;
import static org.niis.xroad.proxy.core.addon.messagelog.ProxyTestUtil.cleanUpDatabase;
import static org.niis.xroad.proxy.core.addon.messagelog.ProxyTestUtil.createMessage;
import static org.niis.xroad.proxy.core.addon.messagelog.ProxyTestUtil.createRestRequest;
import static org.niis.xroad.proxy.core.addon.messagelog.ProxyTestUtil.createSignature;

/**
 * Contains tests to verify correct message log behavior.
 */
@Slf4j
class MessageLogTest extends AbstractMessageLogTest {
    private static final String LAST_LOG_ARCHIVE_FILE = "mlog-20150520112233-20150520123344-asdlfjlasa.zip";
    private static final String LAST_DIGEST = "123567890abcdef";
    private static final String TEST_KEY_ID = "test-key-1";

    @BeforeAll
    static void beforeAll() {
        TimeUtils.setClock(Clock.systemDefaultZone());
    }

    static Date logRecordTime;

    /**
     * Logs a message and timestamps it explicitly.
     *
     * @throws Exception in case of any unexpected errors
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void timestampingForced(boolean encrypted) throws Exception {
        setupTest(encrypted);
        log.trace("timestampingForced(encrypted={})", encrypted);

        log("02-04-2014 12:34:56.100", createMessage("forced"));
        assertTaskQueueSize(databaseCtx, 1);

        MessageRecord messageRecord = findByQueryId("forced");
        assertMessageRecord(messageRecord, "forced");
        assertEncryptionState(messageRecord, encrypted);

        TimestampRecord timestamp = timestamp(messageRecord);
        assertNotNull(timestamp);

        messageRecord = findByQueryId("forced");

        assertEquals(timestamp, messageRecord.getTimestampRecord());
        assertTaskQueueSize(databaseCtx, 0);
    }

    /**
     * Logs a message and calls explicit timestamping on it twice. The returned timestamps must match.
     *
     * @throws Exception in case of any unexpected errors
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void timestampingDouble(boolean encrypted) throws Exception {
        setupTest(encrypted);
        log.trace("timestampingDouble(encrypted={})", encrypted);

        log("02-04-2014 12:34:56.100", createMessage("forced"));
        assertTaskQueueSize(databaseCtx, 1);

        MessageRecord messageRecord = findByQueryId("forced");
        assertMessageRecord(messageRecord, "forced");
        assertEncryptionState(messageRecord, encrypted);

        TimestampRecord timestamp1 = timestamp(messageRecord);
        assertNotNull(timestamp1);

        TimestampRecord timestamp2 = timestamp(messageRecord);
        assertNotNull(timestamp2);

        assertEquals(timestamp1, timestamp2);
    }

    /**
     * Logs 3 messages (message and signature is same) and time-stamps them. Expects 1 time-stamp record and 3 message
     * records that refer to the time-stamp record. The time-stamp record must have hash chains.
     *
     * @throws Exception in case of any unexpected errors
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void logThreeMessagesAndTimestamp(boolean encrypted) throws Exception {
        setupTest(encrypted);
        log.trace("logThreeMessagesAndTimestamp(encrypted={})", encrypted);

        log(createMessage(), createSignature());
        log(createMessage(), createSignature());
        log(createMessage(), createSignature());

        assertTaskQueueSize(databaseCtx, 3);

        startTimestamping();

        TimestampSucceeded timestamp = waitForTimestampSuccessful();
        assertTrue(TestTaskQueue.waitForTimestampSaved());

        assertEquals(3, timestamp.getMessageRecords().length);
        assertNotNull(timestamp.getTimestampDer());
        assertNotNull(timestamp.getHashChainResult());
        assertEquals(3, timestamp.getHashChains().length);

        assertTaskQueueSize(databaseCtx, 0);
    }

    /**
     * Log message
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @SuppressWarnings("squid:S2699")
    void logRestMessage(boolean encrypted) throws Exception {
        setupTest(encrypted);
        log.trace("logRestMessage(encrypted={})", encrypted);

        final String requestId = UUID.randomUUID().toString();
        final RestRequest message = createRestRequest("q-" + requestId, requestId);

        final Instant atDate = TimeUtils.now();
        final byte[] body = "\"test message body\"".getBytes(StandardCharsets.UTF_8);
        log(atDate, message, createSignature(), body);
        final MessageRecord logRecord = findByQueryId(message.getQueryId(), ClientId.Conf.create("XRD", "Class", "Member", "SubSystem"));

        assertEncryptionState(logRecord, encrypted);
        messageRecordEncryption.prepareDecryption(logRecord);
        assertEquals(logRecord.getXRequestId(), requestId);
        assertEquals(logRecord.getQueryId(), message.getQueryId());
        final AsicContainer asic = logRecord.toAsicContainer();
        assertArrayEquals(asic.getMessage().getBytes(StandardCharsets.UTF_8), message.getMessageBytes());
        final byte[] attachment = IOUtils.readFully(asic.getAttachments().getFirst(), body.length);
        assertArrayEquals(body, attachment);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void logSoapWithAttachments(boolean encrypted) throws Exception {
        setupTest(encrypted);
        log.trace("logSoapWithAttachments(encrypted={})", encrypted);

        final String requestId = UUID.randomUUID().toString();
        final var message = createMessage(requestId);
        var attachment1 = "ONE".getBytes(StandardCharsets.UTF_8);
        var attachment2 = "TWO".getBytes(StandardCharsets.UTF_8);

        log(message, createSignature(), List.of(attachment1, attachment2));

        final MessageRecord logRecord = findByQueryId(message.getQueryId());
        assertEncryptionState(logRecord, encrypted);
        messageRecordEncryption.prepareDecryption(logRecord);
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
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testTimestampRecordsLimit(boolean encrypted) throws Exception {
        log.trace("testTimestampRecordsLimit(encrypted={})", encrypted);

        // Reinitialize with custom timestamp-records-limit
        setupTestWithConfig(Map.of("xroad.proxy.message-log.timestamper.records-limit", "2"), encrypted);

        TestTaskQueue.successfulMessageSizes.clear();
        log(createMessage(), createSignature());
        log(createMessage(), createSignature());
        log(createMessage(), createSignature());
        log(createMessage(), createSignature());
        log(createMessage(), createSignature());
        assertTaskQueueSize(databaseCtx, 5);

        startTimestamping();

        assertEquals(List.of(2, 2, 1), TestTaskQueue.successfulMessageSizes);
    }

    /**
     * Timestamps message immediately. No messages are expected to be in the task queue.
     *
     * @throws Exception in case of any unexpected errors
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void timestampImmediately(boolean encrypted) throws Exception {
        log.trace("timestampImmediately(encrypted={})", encrypted);

        // Reinitialize with timestamp-immediately enabled
        setupTestWithConfig(Map.of("xroad.proxy.message-log.timestamper.timestamp-immediately", "true"), encrypted);

        log(createMessage(), createSignature());
        assertTaskQueueSize(databaseCtx, 0);
    }

    /**
     * Timestamps message immediately, but time-stamping fails.
     *
     * @throws Exception in case of any unexpected errors
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void timestampImmediatelyFail(boolean encrypted) throws Exception {
        log.trace("timestampImmediatelyFail(encrypted={})", encrypted);

        // Reinitialize with timestamp-immediately enabled
        setupTestWithConfig(Map.of("xroad.proxy.message-log.timestamper.timestamp-immediately", "true"), encrypted);

        TestTimestamperWorker.failNextTimestamping(true);

        assertThrows(Exception.class, () -> log(createMessage(), createSignature()), "Should fail to timestamp immediately");
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
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void logTimestampArchiveAndClean(boolean encrypted) throws Exception {
        setupTest(encrypted);
        log.trace("logTimestampArchiveAndClean(encrypted={})", encrypted);

        assertTaskQueueSize(databaseCtx, 0);
        log("01-09-2021 12:34:55.100", createMessage(), createSignature());
        log("01-09-2021 12:34:57.200", createMessage(), createSignature());
        log("01-09-2021 12:34:59.300", createMessage(), createSignature());
        assertTaskQueueSize(databaseCtx, 3);

        startTimestamping();
        waitForTimestampSuccessful();
        assertTrue(TestTaskQueue.waitForTimestampSaved());

        assertTaskQueueSize(databaseCtx, 0);

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
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void timestampingFailed(boolean encrypted) throws Exception {
        setupTest(encrypted);
        log.trace("timestampingFailed(encrypted={})", encrypted);

        TestTimestamperWorker.failNextTimestamping(true);

        log(createMessage(), createSignature());
        log(createMessage(), createSignature());
        log(createMessage(), createSignature());
        assertTaskQueueSize(databaseCtx, 3);

        startTimestamping();

        Object result = waitForMessageInTaskQueue();
        assertInstanceOf(TimestampFailed.class, result, "Got " + result);

        log(createMessage(), createSignature());
        assertTaskQueueSize(databaseCtx, 4);
    }

    /**
     * Logs messages, time-stamping failed. After acceptable period no more messages are accepted.
     *
     * @throws Exception in case of any unexpected errors
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void timestampingFailedStopLogging(boolean encrypted) throws Exception {
        log.trace("timestampingFailedStopLogging(encrypted={})", encrypted);

        // Reinitialize with short acceptable timestamp failure period
        setupTestWithConfig(Map.of("xroad.proxy.message-log.timestamper.acceptable-timestamp-failure-period", "1"), encrypted);

        TestTimestamperWorker.failNextTimestamping(true);

        log(createMessage(), createSignature());
        log(createMessage(), createSignature());
        log(createMessage(), createSignature());
        assertTaskQueueSize(databaseCtx, 3);

        logManager.setTimestampFailed(TimeUtils.now().minusSeconds(60));

        startTimestamping();
        waitForMessageInTaskQueue();

        CodedException exception = assertThrows(CodedException.class, () -> log(createMessage(), createSignature()));
        assertEquals(ErrorCode.TIMESTAMPING_FAILED.code(), exception.getFaultCode());
    }

    /**
     * Saving timestamp to database fails.
     *
     * @throws Exception in case of any unexpected errors
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedToSaveTimestampToDatabase(boolean encrypted) throws Exception {
        setupTest(encrypted);
        log.trace("failedToSaveTimestampToDatabase(encrypted={})", encrypted);

        TestTaskQueue.throwWhenSavingTimestamp = new CodedException("expected");

        log(createMessage(), createSignature());
        log(createMessage(), createSignature());
        log(createMessage(), createSignature());

        assertTaskQueueSize(databaseCtx, 3);

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
        assertTaskQueueSize(databaseCtx, 4);
    }

    /**
     * Get message by query id.
     *
     * @throws Exception in case of any unexpected errors
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void findByQueryId(boolean encrypted) throws Exception {
        setupTest(encrypted);
        log.trace("findByQueryId(encrypted={})", encrypted);

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
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void timestampNoTspUrls(boolean encrypted) throws Exception {
        setupTest(encrypted);
        log.trace("timestampNoTspUrls(encrypted={})", encrypted);

        serverConfProvider.setServerConfProvider(new EmptyServerConf());

        CodedException exception = assertThrows(CodedException.class, () -> log(createMessage(), createSignature()));
        assertEquals(ErrorCode.NO_TIMESTAMPING_PROVIDER_FOUND.code(), exception.getFaultCode());
    }


    /**
     * Test that verifies actual encryption state in database.
     * When encrypted=true, cipherMessage should be set and different from plaintext.
     * When encrypted=false, message should be set in plaintext.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldStoreMessagesAccordingToEncryptionMode(boolean encrypted) throws Exception {
        setupTest(encrypted);
        log.trace("shouldStoreMessagesAccordingToEncryptionMode(encrypted={})", encrypted);

        SoapMessageImpl soapMessage = createMessage("encryption-test");

        log(soapMessage, createSignature());

        MessageRecord messageRecord = findByQueryId("encryption-test");
        assertNotNull(messageRecord);

        if (encrypted) {
            // Verify encrypted storage
            assertNotNull(messageRecord.getCipherMessage(), "Encrypted message should have cipherMessage");
            assertNull(messageRecord.getMessage(), "Encrypted message should have null plaintext message");
            assertEquals(TEST_KEY_ID, messageRecord.getKeyId(), "Encrypted message should have correct key ID");

            // Verify ciphertext is different from plaintext
            assertFalse(
                    java.util.Arrays.equals(soapMessage.getXml().getBytes(StandardCharsets.UTF_8),
                            messageRecord.getCipherMessage()),
                    "Ciphertext should be different from plaintext"
            );
        } else {
            // Verify plaintext storage
            assertNotNull(messageRecord.getMessage(), "Unencrypted message should have plaintext message");
            assertNull(messageRecord.getCipherMessage(), "Unencrypted message should have null cipherMessage");
            assertEquals(soapMessage.getXml(), messageRecord.getMessage(), "Plaintext message should match original");
        }
    }

    /**
     * Test that verifies attachment encryption works correctly.
     * Encrypted attachments should be different from plaintext but preserve length.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldEncryptAttachmentsCorrectly(boolean encrypted) throws Exception {
        setupTest(encrypted);
        log.trace("shouldEncryptAttachmentsCorrectly(encrypted={})", encrypted);

        String requestId = UUID.randomUUID().toString();
        SoapMessageImpl message = createMessage(requestId);
        byte[] attachment1 = "First attachment data".getBytes(StandardCharsets.UTF_8);
        byte[] attachment2 = "Second attachment with more content".getBytes(StandardCharsets.UTF_8);

        log(message, createSignature(), List.of(attachment1, attachment2));

        MessageRecord logRecord = findByQueryId(message.getQueryId());
        assertNotNull(logRecord);
        assertEncryptionState(logRecord, encrypted);

        if (encrypted) {
            // Verify attachments are encrypted in storage
            assertEquals(TEST_KEY_ID, logRecord.getKeyId(), "Encrypted record should have correct key ID");
            assertEquals(2, logRecord.getAttachments().size(), "Should have 2 attachments");

            // Get raw attachment data from database (before decryption)
            var att1 = logRecord.getAttachments().get(0);
            var att2 = logRecord.getAttachments().get(1);

            assertNotNull(att1.getAttachment(), "Attachment 1 should have encrypted data");
            assertNotNull(att2.getAttachment(), "Attachment 2 should have encrypted data");

            // CTR mode preserves length
            assertEquals(attachment1.length, att1.getAttachment().length(), "CTR mode should preserve attachment 1 length");
            assertEquals(attachment2.length, att2.getAttachment().length(), "CTR mode should preserve attachment 2 length");
        }

        // Verify decryption works correctly
        messageRecordEncryption.prepareDecryption(logRecord);
        AsicContainer asic = logRecord.toAsicContainer();
        var decryptedAttachments = asic.getAttachments().stream().map(MessageLogTest::readAllBytes).toList();

        assertArrayEquals(attachment1, decryptedAttachments.get(0), "Attachment 1 should decrypt correctly");
        assertArrayEquals(attachment2, decryptedAttachments.get(1), "Attachment 2 should decrypt correctly");
    }

    /**
     * Test that verifies key ID is correctly stored and retrieved.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldStoreAndRetrieveKeyIdCorrectly(boolean encrypted) throws Exception {
        setupTest(encrypted);
        log.trace("shouldStoreAndRetrieveKeyIdCorrectly(encrypted={})", encrypted);

        // Log multiple messages
        log(createMessage("msg1"), createSignature());
        log(createMessage("msg2"), createSignature());
        log(createMessage("msg3"), createSignature());

        // Verify all messages have correct key ID
        MessageRecord msg1 = findByQueryId("msg1");
        MessageRecord msg2 = findByQueryId("msg2");
        MessageRecord msg3 = findByQueryId("msg3");

        assertNotNull(msg1);
        assertNotNull(msg2);
        assertNotNull(msg3);

        if (encrypted) {
            assertEquals(TEST_KEY_ID, msg1.getKeyId(), "Message 1 should have correct key ID");
            assertEquals(TEST_KEY_ID, msg2.getKeyId(), "Message 2 should have correct key ID");
            assertEquals(TEST_KEY_ID, msg3.getKeyId(), "Message 3 should have correct key ID");

            // Verify all messages are encrypted
            assertNotNull(msg1.getCipherMessage());
            assertNotNull(msg2.getCipherMessage());
            assertNotNull(msg3.getCipherMessage());
        } else {
            // When not encrypted, key ID may be null or not set
            // Just verify messages are in plaintext
            assertNotNull(msg1.getMessage());
            assertNotNull(msg2.getMessage());
            assertNotNull(msg3.getMessage());
        }
    }

    // ------------------------------------------------------------------------

    /**
     * Set up test with specific encryption mode.
     *
     * @param encrypted whether to enable encryption
     * @throws Exception in case of any unexpected errors
     */
    protected void setupTest(boolean encrypted) throws Exception {
        // Initialize with test-specific configuration
        Map<String, String> config = new java.util.HashMap<>();
        config.put("xroad.proxy.message-log.timestamper.timestamp-immediately", "false");
        config.put("xroad.proxy.message-log.timestamper.acceptable-timestamp-failure-period", "1800");
        config.put("xroad.proxy.message-log.archiver.grouping-strategy", GroupingStrategy.MEMBER.name());
        config.put("xroad.proxy.message-log.archiver.archive-path", "build/archive");
        config.put("xroad.proxy.message-log.archiver.clean-keep-records-for", "0");
        config.put("xroad.proxy.message-log.database-encryption.enabled", String.valueOf(encrypted));

        if (encrypted) {
            config.put("xroad.proxy.message-log.database-encryption.key-id", TEST_KEY_ID);
        }

        testSetUp(config, encrypted);

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

    protected void setupTestWithConfig(Map<String, String> additionalConfig, boolean encrypted) throws Exception {
        Map<String, String> config = new java.util.HashMap<>(additionalConfig);
        config.put("xroad.proxy.message-log.database-encryption.enabled", String.valueOf(encrypted));

        if (encrypted) {
            config.put("xroad.proxy.message-log.database-encryption.key-id", TEST_KEY_ID);
        }

        testSetUp(config, encrypted);

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

    private void initLastHashStep() {
        var lastArchive = new DigestEntryEmbeddable();
        lastArchive.setDigest(LAST_DIGEST);
        lastArchive.setFileName(LAST_LOG_ARCHIVE_FILE);
        var digest = new ArchiveDigestEntity();
        digest.setGroupName(ClientId.Conf.create("XRD", "BUSINESS", "consumer").toShortString());
        digest.setDigestEntry(lastArchive);
        databaseCtx.doInTransaction(session -> {
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
    @AfterEach
    void tearDown() throws Exception {
        if (databaseCtx != null) {
            cleanUpDatabase(databaseCtx);
        }
        if (logManager != null) {
            testTearDown();
        }
    }

    @Override
    protected boolean useTestLogManager() {
        return true;
    }

    protected void log(String atDate, SoapMessageImpl message) throws Exception {
        log(atDate, message, createSignature());
    }

    protected void log(String atDate, SoapMessageImpl message, SignatureData signature) throws Exception {
        logRecordTime = getDate(atDate);
        log(message, signature);
    }

    protected void log(Instant instant, RestRequest message, SignatureData signatureData, byte[] body) {
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

    protected MessageRecord findByQueryId(String queryId) {
        ClientId clientId = ClientId.Conf.create("XRD", "BUSINESS", "consumer");
        return logRecordManager.getByQueryIdUnique(queryId, clientId, false, MessageLogTest::initializeAttachments);
    }

    protected MessageRecord findByQueryId(String queryId, ClientId clientId) {
        return logRecordManager.getByQueryIdUnique(queryId, clientId, false, MessageLogTest::initializeAttachments);
    }

    private String getLastEntryDeleteQuery() {
        return "delete from " + ArchiveDigestEntity.class.getName();
    }

    private void assertArchiveHashChain() throws Exception {
        String archiveFilePath = getArchiveFilePath();

        final MessageDigest digest = MessageDigest.getInstance(messageLogProperties.hashAlg().name());
        final Map<String, byte[]> digests = new HashMap<>();
        String linkinginfo = null;

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(Paths.get(archiveFilePath)))) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                digest.reset();
                final byte[] buf = new byte[4096];
                int len;
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
        assert linkinginfo != null;
        for (final String line : linkinginfo.split("\n")) {
            final String[] parts = line.split("\\s+");
            if (prevHash == null) {
                prevHash = parts[0];
                assertEquals(LAST_DIGEST, prevHash);
                assertEquals(messageLogProperties.hashAlg(), DigestAlgorithm.ofName(parts[2]));
            } else {
                digest.reset();
                digest.update(prevHash.getBytes());
                final byte[] d = digests.get(parts[1]);
                assertNotNull(d, "Archive did not contain file " + parts[1]);
                digest.update(EncoderUtils.encodeHex(d).getBytes());
                prevHash = EncoderUtils.encodeHex(digest.digest());
                assertEquals(parts[0], prevHash, "Digest does not match");
            }
        }

        String lastStepInDatabase = getLastHashStepInDatabase();

        assertEquals(lastStepInDatabase, prevHash, "Last hash step file must start with last hash step result");
    }

    private String getLastHashStepInDatabase() {
        return databaseCtx.doInTransaction(session -> session
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

        assert files != null;
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

    private int getNumberOfRecords(final boolean archived) {
        return databaseCtx.doInTransaction(session -> {
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

    /**
     * Assert that the message record has the correct encryption state.
     *
     * @param messageRecord the message record to check
     * @param encrypted     whether encryption should be enabled
     */
    private void assertEncryptionState(MessageRecord messageRecord, boolean encrypted) {
        if (encrypted) {
            // When encrypted, cipherMessage should be set and message should be null
            assertNotNull(messageRecord.getCipherMessage(), "Encrypted record should have cipherMessage");
            assertNull(messageRecord.getMessage(), "Encrypted record should have null message");
            assertEquals(TEST_KEY_ID, messageRecord.getKeyId(), "Encrypted record should have correct key ID");
        } else {
            // When not encrypted, message should be set and cipherMessage should be null
            assertNotNull(messageRecord.getMessage(), "Unencrypted record should have message");
            assertNull(messageRecord.getCipherMessage(), "Unencrypted record should have null cipherMessage");
        }
    }

}
