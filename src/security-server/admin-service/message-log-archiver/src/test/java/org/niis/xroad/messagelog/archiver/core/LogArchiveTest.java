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
package org.niis.xroad.messagelog.archiver.core;

import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.identifier.ClientId;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.niis.xroad.common.pgp.BouncyCastlePgpEncryptionService;
import org.niis.xroad.common.pgp.PgpKeyGenerator;
import org.niis.xroad.common.pgp.PgpKeyManager;
import org.niis.xroad.common.pgp.PgpKeyProvider;
import org.niis.xroad.common.pgp.PgpKeyResolver;
import org.niis.xroad.common.pgp.StreamingPgpEncryptor;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.messagelog.LogRecord;
import org.niis.xroad.messagelog.MessageRecord;
import org.niis.xroad.messagelog.TimestampRecord;
import org.niis.xroad.messagelog.archive.DisabledEncryptionConfigProvider;
import org.niis.xroad.messagelog.archive.EncryptionConfigProvider;
import org.niis.xroad.messagelog.archive.GroupingStrategy;
import org.niis.xroad.messagelog.archive.VaultServerEncryptionConfigProvider;
import org.niis.xroad.messagelog.archiver.core.config.LogArchiverExecutionProperties;
import org.niis.xroad.test.globalconf.EmptyGlobalConf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises entire logic of archiving log entries. Actually depends on
 * LogArchiveCacheTest.
 */
class LogArchiveTest {

    private static final int NUM_TIMESTAMPS = 3;
    private static final int NUM_RECORDS_PER_TIMESTAMP = 5;

    private boolean rotated;
    private long recordNo;
    private GlobalConfProvider globalConfProvider;
    private EncryptionConfigProvider encryptionConfigProvider;

    static Stream<Arguments> params() {
        return Stream.of(
                Arguments.of(false, GroupingStrategy.NONE),
                Arguments.of(false, GroupingStrategy.MEMBER),
                Arguments.of(false, GroupingStrategy.SUBSYSTEM),
                Arguments.of(true, GroupingStrategy.NONE),
                Arguments.of(true, GroupingStrategy.MEMBER),
                Arguments.of(true, GroupingStrategy.SUBSYSTEM)
        );
    }

    @BeforeEach
    void setup() throws Exception {
        globalConfProvider = new EmptyGlobalConf() {
            @Override
            public String getInstanceIdentifier() {
                return "INSTANCE";
            }
        };

        recordNo = 0;
        rotated = false;

        // Create log directory if it doesn't exist
        if (!Files.exists(Paths.get("build/slog"))) {
            Files.createDirectory(Paths.get("build/slog"));
        }
    }

    @AfterEach
    void tearDown() {
        FileUtils.deleteQuietly(Paths.get("build/slog").toFile());
    }

    // ------------------------------------------------------------------------

    /**
     * Writes many records and rotates to new file.
     *
     * @throws Exception - when cannot either write or rotate
     */
    @ParameterizedTest(name = "[{index}] encrypted = {0}, grouping = {1}")
    @MethodSource("params")
    void writeAndRotate(boolean encrypted, GroupingStrategy groupingStrategy) throws Exception {
        // Given
        initializeEncryptionConfig(encrypted);

        // When
        writeRecordsToLog(false, 50L, groupingStrategy);

        // Then
        assertTrue(rotated);
    }

    /**
     * Writes records, simulates a situation where archiving is finished just after rotate.
     * (XRDDEV-85)
     */
    @ParameterizedTest(name = "[{index}] encrypted = {0}, grouping = {1}")
    @MethodSource("params")
    void testFinishAfterRotate(boolean encrypted, GroupingStrategy groupingStrategy) throws Exception {
        // Given
        initializeEncryptionConfig(encrypted);

        // When
        writeRecordsToLog(true, 3000L, groupingStrategy);

        // Then
        assertTrue(rotated);
    }

    // ------------------------------------------------------------------------

    private void initializeEncryptionConfig(boolean encrypted) {
        if (encrypted) {
            // Initialize BouncyCastle PGP encryption components
            PgpKeyGenerator keyGenerator = new PgpKeyGenerator();
            var keyInfo = keyGenerator.generate("Test Archive <test@archive.org>");

            // Create mock key provider
            var keyProvider = mock(PgpKeyProvider.class);
            when(keyProvider.getSigningSecretKey()).thenReturn(keyInfo.secretData());
            when(keyProvider.getEncryptionPublicKeys()).thenReturn(Optional.of(keyInfo.publicData()));

            // Initialize key manager
            var keyManager = new PgpKeyManager(keyProvider);

            // Initialize encryption service
            var keyResolver = new PgpKeyResolver(keyManager);
            var encryptor = new StreamingPgpEncryptor();
            var encryptionService = new BouncyCastlePgpEncryptionService(keyManager, keyResolver, encryptor);

            // Create encryption config provider
            encryptionConfigProvider = new VaultServerEncryptionConfigProvider(keyManager, encryptionService);
        } else {
            encryptionConfigProvider = new DisabledEncryptionConfigProvider();
        }
    }

    private void writeRecordsToLog(boolean finishAfterRotate, long maxFilesize, GroupingStrategy groupingStrategy) throws Exception {
        try (LogArchiveWriter writer = createWriter(maxFilesize, groupingStrategy)) {
            outer:
            for (int i = 0; i < NUM_TIMESTAMPS; i++) {
                TimestampRecord ts = nextTimestampRecord();
                for (int j = 0; j < NUM_RECORDS_PER_TIMESTAMP; j++) {
                    MessageRecord messageRecord = nextMessageRecord();
                    messageRecord.setTimestampRecord(ts);
                    messageRecord.setTimestampHashChain("foo");

                    if (writer.write(messageRecord) && finishAfterRotate) {
                        break outer;
                    }
                }
            }
        }
    }

    private LogArchiveWriter createWriter(long maxFilesize, GroupingStrategy groupingStrategy) {
        LogArchiverExecutionProperties executionProperties = createExecutionProperties(maxFilesize, groupingStrategy);
        return new LogArchiveWriter(globalConfProvider,
                Paths.get("build/slog"),
                dummyLogArchiveBase(),
                encryptionConfigProvider,
                executionProperties) {

            @Override
            protected void rotate() throws IOException {
                super.rotate();
                rotated = true;
            }
        };
    }

    private LogArchiverExecutionProperties createExecutionProperties(long maxFilesize, GroupingStrategy groupingStrategy) {
        var archiveEncryption = new LogArchiverExecutionProperties.ArchiveEncryptionProperties(
                false,
                Optional.empty(),
                groupingStrategy,
                Map.of()
        );

        var databaseEncryption = new LogArchiverExecutionProperties.DatabaseEncryptionProperties(
                false,
                null
        );

        return new LogArchiverExecutionProperties(
                archiveEncryption,
                databaseEncryption,
                100,
                30,
                100,
                "build/slog",
                null,
                DigestAlgorithm.SHA512,
                maxFilesize,
                "build/tmp"
        );
    }

    private LogArchiveBase dummyLogArchiveBase() {
        return new LogArchiveBase() {
            @Override
            public void markArchiveCreated(String entryName, DigestEntry lastArchive) {
                // Do nothing.
            }

            @Override
            public void markRecordArchived(LogRecord logRecord) {
                // Do nothing.
            }

            @Override
            public DigestEntry loadLastArchive(String entryName) {
                return DigestEntry.empty();
            }
        };
    }

    private MessageRecord nextMessageRecord() {
        recordNo++;

        MessageRecord messageRecord = new MessageRecord("qid" + recordNo,
                "msg" + recordNo, "sig" + recordNo, false,
                ClientId.Conf.create(globalConfProvider.getInstanceIdentifier(), "memberClass", "memberCode", "subsystemCode"),
                "92060130-3ba8-4e35-89e2-41b90aac074b");
        messageRecord.setId(recordNo);
        messageRecord.setTime(RandomUtils.insecure().randomLong());

        return messageRecord;
    }

    private TimestampRecord nextTimestampRecord() {
        recordNo++;

        TimestampRecord timestampRecord = new TimestampRecord();
        timestampRecord.setId(recordNo);
        timestampRecord.setTimestamp("dGltZXN0YW1w");
        timestampRecord.setHashChainResult("foo");
        timestampRecord.setTime(RandomUtils.insecure().randomLong());

        return timestampRecord;
    }
}
