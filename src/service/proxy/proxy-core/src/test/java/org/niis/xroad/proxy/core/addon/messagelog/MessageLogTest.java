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
import org.mockito.MockedStatic;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.NodeProperties;
import org.niis.xroad.messagelog.LogRecord;
import org.niis.xroad.messagelog.MessageRecord;
import org.niis.xroad.messagelog.RestLogMessage;
import org.niis.xroad.messagelog.TimestampRecord;
import org.niis.xroad.messagelog.archive.GroupingStrategy;
import org.niis.xroad.messagelog.archiver.core.TestLogArchiver;
import org.niis.xroad.messagelog.archiver.core.TestLogCleaner;
import org.niis.xroad.messagelog.entity.AbstractLogRecordEntity;
import org.niis.xroad.messagelog.entity.ArchiveDigestEntity;
import org.niis.xroad.messagelog.entity.DigestEntryEmbeddable;
import org.niis.xroad.messagelog.entity.TimestampingStatusEntity;
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
import java.time.ZoneOffset;
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
import static org.mockito.Mockito.mockStatic;
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
        final byte[] attachment = IOUtils.toByteArray(asic.getAttachments().getFirst(), body.length);
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
     * Timestamps message immediately on a secondary cluster node, where the periodic batch timestamper never starts.
     *
     * @throws Exception in case of any unexpected errors
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void timestampImmediatelyOnSecondaryNode(boolean encrypted) throws Exception {
        log.trace("timestampImmediatelyOnSecondaryNode(encrypted={})", encrypted);

        try (MockedStatic<NodeProperties> nodeProperties = mockStatic(NodeProperties.class)) {
            nodeProperties.when(NodeProperties::isSecondaryNode).thenReturn(true);
            setupTestWithConfig(Map.of("xroad.proxy.message-log.timestamper.timestamp-immediately", "true"), encrypted);
        }

        assertNull(logManager.timestamperJob);

        log(createMessage(), createSignature());
        assertTaskQueueSize(databaseCtx, 0);
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
     * Logs messages the way a secondary cluster node would, with no record of which node wrote them, then
     * time-stamps, archives and cleans them on a primary node.
     *
     * @throws Exception in case of any unexpected errors
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void logTimestampArchiveAndCleanRecordsWithoutNodeAffinity(boolean encrypted) throws Exception {
        log.trace("logTimestampArchiveAndCleanRecordsWithoutNodeAffinity(encrypted={})", encrypted);

        try (MockedStatic<NodeProperties> nodeProperties = mockStatic(NodeProperties.class)) {
            nodeProperties.when(NodeProperties::isSecondaryNode).thenReturn(false);
            setupTest(encrypted);
        }

        assertNotNull(logManager.timestamperJob);

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
     * Logging refuses once the shared row's recorded failure is older than the acceptable period,
     * and accepts again just inside it.
     *
     * @throws Exception in case of any unexpected errors
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void timestampingFailedStopLogging(boolean encrypted) throws Exception {
        log.trace("timestampingFailedStopLogging(encrypted={})", encrypted);

        final int period = 10;
        // Reinitialize with short acceptable timestamp failure period
        setupTestWithConfig(Map.of(
                "xroad.proxy.message-log.timestamper.acceptable-timestamp-failure-period", String.valueOf(period)), encrypted);

        writeTimestampingStatusRow(TimeUtils.now().minusSeconds(period + 5));

        SoapMessageImpl message = createMessage();
        SignatureData signature = createSignature();
        XrdRuntimeException xrdRuntimeException = assertThrows(XrdRuntimeException.class, () -> log(message, signature));
        assertEquals(ErrorCode.TIMESTAMPING_FAILED.code(), xrdRuntimeException.getErrorCode());

        writeTimestampingStatusRow(TimeUtils.now().minusSeconds(period - 5));

        log(createMessage(), createSignature());
    }

    /**
     * A freshly started primary, as after a restart or a failover, refuses at once when the row
     * left by an earlier run holds a failure older than the acceptable period.
     *
     * @throws Exception in case of any unexpected errors
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void primaryRestartRefusesOnRowLeftByEarlierRun(boolean encrypted) throws Exception {
        final int period = 10;
        setupTestWithConfig(Map.of(
                "xroad.proxy.message-log.timestamper.acceptable-timestamp-failure-period", String.valueOf(period)), encrypted);

        writeTimestampingStatusRow(TimeUtils.now().minusSeconds(period + 5));

        XrdRuntimeException xrdRuntimeException = assertThrows(XrdRuntimeException.class, () -> log(createMessage(), createSignature()));
        assertEquals(ErrorCode.TIMESTAMPING_FAILED.code(), xrdRuntimeException.getErrorCode());
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

        TestTaskQueue.throwWhenSavingTimestamp = XrdRuntimeException.systemInternalError("expected");

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

        assertNotNull(getTimestampingStatusRow());

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

        XrdRuntimeException exception = assertThrows(XrdRuntimeException.class, () -> log(createMessage(), createSignature()));
        assertEquals(ErrorCode.NO_TIMESTAMPING_PROVIDER_FOUND.code(), exception.getErrorCode());
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

    /**
     * Logs no messages, so the batch run finds nothing to time-stamp. The success recorded for
     * the empty queue must clear a failure that an earlier run left in the row, as a restarted
     * primary or a new primary after failover finds it.
     *
     * @throws Exception in case of any unexpected errors
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void primaryEmptyQueueClearsFailureLeftByEarlierRun(boolean encrypted) throws Exception {
        setupTest(encrypted);

        writeTimestampingStatusRow(TimeUtils.now().minusSeconds(120));
        assertTaskQueueSize(databaseCtx, 0);

        startTimestamping();

        assertNull(getTimestampingStatusRow());
    }

    /**
     * Logs a message, time-stamping fails twice in a row. The row does not exist beforehand, so
     * the first failure must insert it with the failure start time; the second failure must keep
     * that time.
     *
     * @throws Exception in case of any unexpected errors
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void primaryFailureSetsFailureStartAndKeepsItOnSecondFailure(boolean encrypted) throws Exception {
        setupTest(encrypted);

        assertNull(getTimestampingStatusRow());

        TestTimestamperWorker.failNextTimestamping(true);
        log(createMessage(), createSignature());
        assertTaskQueueSize(databaseCtx, 1);

        startTimestamping();
        waitForMessageInTaskQueue();

        TimestampingStatusEntity firstFailure = getTimestampingStatusRow();
        assertNotNull(firstFailure);
        assertNotNull(firstFailure.getFailedSince());

        TestTimestamperWorker.failNextTimestamping(true);
        startTimestamping();
        waitForMessageInTaskQueue();

        TimestampingStatusEntity secondFailure = getTimestampingStatusRow();
        assertEquals(firstFailure.getFailedSince(), secondFailure.getFailedSince());
    }

    /**
     * Logs a message and time-stamps it successfully. The success must clear a previously
     * recorded failure.
     *
     * @throws Exception in case of any unexpected errors
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void primarySuccessClearsRecordedFailure(boolean encrypted) throws Exception {
        setupTest(encrypted);

        writeTimestampingStatusRow(TimeUtils.now().minusSeconds(120));

        log(createMessage(), createSignature());
        assertTaskQueueSize(databaseCtx, 1);

        startTimestamping();
        waitForTimestampSuccessful();
        assertTrue(TestTaskQueue.waitForTimestampSaved());

        assertNull(getTimestampingStatusRow());
    }

    /**
     * Timestamp-immediately mode never writes the shared state row, even when the batch job's
     * own periodic (empty-queue) run fires on the primary: a recorded failure is left as it is.
     *
     * @throws Exception in case of any unexpected errors
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void primaryTimestampImmediatelyDoesNotWriteRow(boolean encrypted) throws Exception {
        setupTestWithConfig(Map.of("xroad.proxy.message-log.timestamper.timestamp-immediately", "true"), encrypted);
        assertTaskQueueSize(databaseCtx, 0);
        writeTimestampingStatusRow(TimeUtils.now().minusSeconds(120));
        Instant recordedFailure = getTimestampingStatusRow().getFailedSince();
        assertNotNull(recordedFailure);

        startTimestamping();

        assertEquals(recordedFailure, getTimestampingStatusRow().getFailedSince());
    }

    /**
     * A SUCCESS whose own synchronous recovery batch ({@code TimestamperJob.onSuccess} ->
     * {@code TaskQueue.handleStartTimestamping}) fails must still leave the failure recorded: the
     * recovery batch's write happens after the outer success write and must not be overwritten
     * by it.
     *
     * @throws Exception in case of any unexpected errors
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void primarySuccessWithFailingRecoveryBatchLeavesFailureSet(boolean encrypted) throws Exception {
        final int period = 10;
        setupTestWithConfig(Map.of(
                "xroad.proxy.message-log.timestamper.acceptable-timestamp-failure-period", String.valueOf(period)), encrypted);

        TestTimestamperWorker.failNextTimestamping(true);
        log(createMessage(), createSignature());
        assertTaskQueueSize(databaseCtx, 1);

        startTimestamping();
        waitForMessageInTaskQueue();

        assertNotNull(getTimestampingStatusRow().getFailedSince());

        TestTimestamperWorker.failNextTimestamping(true);
        logManager.setTimestampingStatus(new SetTimestampingStatusMessage(SetTimestampingStatusMessage.Status.SUCCESS));

        Instant recordedFailure = getTimestampingStatusRow().getFailedSince();
        assertNotNull(recordedFailure);

        Clock previousClock = TimeUtils.getClock();
        try {
            TimeUtils.setClock(Clock.fixed(recordedFailure.plusSeconds(period + 5L), ZoneOffset.UTC));

            SoapMessageImpl message = createMessage();
            SignatureData signature = createSignature();
            XrdRuntimeException xrdRuntimeException =
                    assertThrows(XrdRuntimeException.class, () -> log(message, signature));
            assertEquals(ErrorCode.TIMESTAMPING_FAILED.code(), xrdRuntimeException.getErrorCode());
        } finally {
            TimeUtils.setClock(previousClock);
        }
    }

    /**
     * A secondary node refuses once the shared failure start time is older than the acceptable
     * period, and accepts again just inside it.
     *
     * @throws Exception in case of any unexpected errors
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void secondaryRefusesWhenFailureStartOlderThanPeriod(boolean encrypted) throws Exception {
        final int period = 1800;
        setupTestWithConfig(Map.of(
                "xroad.proxy.message-log.timestamper.acceptable-timestamp-failure-period", String.valueOf(period)), encrypted);

        writeTimestampingStatusRow(TimeUtils.now().minusSeconds(period + 10));

        try (MockedStatic<NodeProperties> nodeProperties = mockStatic(NodeProperties.class)) {
            nodeProperties.when(NodeProperties::isSecondaryNode).thenReturn(true);

            XrdRuntimeException exception =
                    assertThrows(XrdRuntimeException.class, () -> logManager.verifyCanLogMessage(false));
            assertEquals(ErrorCode.TIMESTAMPING_FAILED.code(), exception.getErrorCode());
        }

        writeTimestampingStatusRow(TimeUtils.now().minusSeconds(period - 10));

        try (MockedStatic<NodeProperties> nodeProperties = mockStatic(NodeProperties.class)) {
            nodeProperties.when(NodeProperties::isSecondaryNode).thenReturn(true);

            logManager.verifyCanLogMessage(false);
        }
    }

    /**
     * A secondary node accepts messages while the shared state row does not exist.
     *
     * @throws Exception in case of any unexpected errors
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void secondaryAcceptsWhileStateRowIsMissing(boolean encrypted) throws Exception {
        setupTestWithConfig(Map.of(
                "xroad.proxy.message-log.timestamper.acceptable-timestamp-failure-period", "1800"), encrypted);

        deleteTimestampingStatusRow();

        try (MockedStatic<NodeProperties> nodeProperties = mockStatic(NodeProperties.class)) {
            nodeProperties.when(NodeProperties::isSecondaryNode).thenReturn(true);

            logManager.verifyCanLogMessage(false);
        }
    }

    private TimestampingStatusEntity getTimestampingStatusRow() {
        return databaseCtx.doInTransaction(session ->
                session.find(TimestampingStatusEntity.class, TimestampingStatusEntity.SINGLETON_ID));
    }

    private void writeTimestampingStatusRow(Instant failedSince) {
        databaseCtx.doInTransaction(session -> {
            TimestampingStatusEntity entity = session.find(TimestampingStatusEntity.class, TimestampingStatusEntity.SINGLETON_ID);
            if (entity == null) {
                entity = new TimestampingStatusEntity();
                entity.setFailedSince(failedSince);
                session.persist(entity);
            } else {
                entity.setFailedSince(failedSince);
            }
            return null;
        });
    }

    private void deleteTimestampingStatusRow() {
        databaseCtx.doInTransaction(session ->
                session.createMutationQuery("delete from " + TimestampingStatusEntity.class.getName()).executeUpdate());
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

        config.put("xroad.message-log-archiver.archive-path", "build/archive");
        config.put("xroad.message-log-archiver.clean-keep-records-for", "0");

        config.put("xroad.message-log-encryption.archive.grouping-strategy", GroupingStrategy.MEMBER.name());
        config.put("xroad.message-log-encryption.db.encryption-enabled", String.valueOf(encrypted));

        if (encrypted) {
            config.put("xroad.message-log-encryption.db.key-id", TEST_KEY_ID);
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
        config.put("xroad.message-log-encryption.db.encryption-enabled", String.valueOf(encrypted));

        if (encrypted) {
            config.put("xroad.message-log-encryption.db.key-id", TEST_KEY_ID);
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
