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

import ee.ria.xroad.common.asic.AsicContainer;
import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.messagelog.MessageRecord;

import lombok.RequiredArgsConstructor;
import org.apache.commons.io.IOUtils;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.niis.xroad.common.messagelog.archive.EncryptionConfig;
import org.niis.xroad.common.messagelog.archive.GroupingStrategy;
import org.niis.xroad.common.messagelog.archive.VaultServerEncryptionConfigProvider;
import org.niis.xroad.common.pgp.BouncyCastlePgpEncryptionService;
import org.niis.xroad.common.pgp.PgpKeyGenerator;
import org.niis.xroad.common.pgp.PgpKeyManager;
import org.niis.xroad.common.pgp.PgpKeyProvider;
import org.niis.xroad.common.pgp.PgpKeyResolver;
import org.niis.xroad.common.pgp.StreamingPgpEncryptor;
import org.niis.xroad.messagelog.archiver.core.config.LogArchiverExecutionProperties;
import org.niix.xroad.common.pgp.test.StreamingPgpDecryptor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for LogArchiveCache with both encrypted and unencrypted scenarios.
 */
class LogArchiveCacheTest {
    private static final int TOO_LARGE_CONTAINER_SIZE = 10000;
    private static final int ARCHIVE_SIZE_SMALL = 50;
    private static final int ARCHIVE_SIZE_MEDIUM = 350;

    private static final String ENTRY_NAME_REQUEST_NORMAL = "ID1-request-";
    private static final String ENTRY_NAME_REQUEST_LARGE = "ID2-request-";
    private static final String ENTRY_NAME_RESPONSE_NORMAL = "ID3-response-";
    private static final String ENTRY_NAME_REQUEST_CONFLICTING = "ID1-request-";

    private static final long LOG_TIME_REQUEST_NORMAL_LATEST = 1428664947372L;
    private static final long LOG_TIME_REQUEST_LARGE_EARLIEST = 1428664660610L;
    private static final long LOG_TIME_RESPONSE_NORMAL = 1428664927050L;

    private static final long DEFAULT_ARCHIVE_MAX_FILESIZE = 33554432L;

    private long id = 0;
    private boolean encrypted;
    private LogArchiveCache cache;
    private long archiveMaxFilesize = DEFAULT_ARCHIVE_MAX_FILESIZE;

    // Encryption components (initialized when encrypted = true)
    private PgpKeyManager keyManager;
    private StreamingPgpDecryptor decryptor;

    @BeforeEach
    void setup() {
        id = 0;
        archiveMaxFilesize = DEFAULT_ARCHIVE_MAX_FILESIZE;
    }

    @AfterEach
    void tearDown() {
        if (cache != null) {
            cache.close();
        }
    }

    /**
     * Test to ensure one entry of normal size can be added successfully.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void addOneEntryOfNormalSize(boolean useEncryption) throws Exception {
        // Given
        encrypted = useEncryption;
        setMaxArchiveSizeDefault();
        cache = createCache();

        // When
        cache.add(createRequestRecordNormal());

        // Then
        assertFalse(cache.isRotating(), "Should not rotate, as entry is small enough to fit in.");

        Date expectedCreationTime = normalRequestCreationTime();
        assertEquals(expectedCreationTime, cache.getStartTime());
        assertEquals(expectedCreationTime, cache.getEndTime());
        assertZip(expectedNormalSizeRequestEntryName(), getArchiveBytes());
    }

    /**
     * Test to ensure log archive is rotated if an entry is too large.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rotateImmediatelyWhenOneEntryIsTooLarge(boolean useEncryption) throws Exception {
        // Given
        encrypted = useEncryption;
        setMaxArchiveSizeSmall();
        cache = createCache();

        // When
        cache.add(createRequestRecordTooLarge());

        // Then
        assertTrue(cache.isRotating(), "Entry is so large that rotation must take place");
        assertZip(expectedLargeSizeRequestEntryName(), getArchiveBytes());
    }

    /**
     * Test to ensure null message records are not allowed.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void doNotAllowNullMessageRecords(boolean useEncryption) {
        // Given
        encrypted = useEncryption;
        cache = createCache();

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> cache.add(null));
    }

    /**
     * Test to ensure the log archive is rotated inbetween log entry additions.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void addMultipleRecordsWithRotationMeanwhile(boolean useEncryption) throws Exception {
        // Given
        encrypted = useEncryption;
        setMaxArchiveSizeMedium();
        cache = createCache();

        // First record
        cache.add(createRequestRecordNormal());
        assertFalse(cache.isRotating(), "Step 1: no need to rotate yet.");

        // Second record
        cache.add(createRequestRecordTooLarge());

        assertTrue(cache.isRotating(), "Step 2: should be rotated.");
        assertEquals(largeRequestCreationTime(), cache.getStartTime());
        assertEquals(normalRequestCreationTime(), cache.getEndTime());
        assertZip(expectedNormalAndLargeRequestEntryNames(), getArchiveBytes());

        // Third record
        cache.add(createResponseRecordNormal());

        assertFalse(cache.isRotating(), "Step 3: new rotation.");
        assertEquals(normalResponseCreationTime(), cache.getStartTime());
        assertEquals(normalResponseCreationTime(), cache.getEndTime());
        assertZip(expectedNormalSizeResponseEntryName(), getArchiveBytes());
    }

    /**
     * Test to ensure name clash is avoided when fileName already exists in ZIP.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void avoidNameClashWhenFileWithSameNameIsAlreadyInSameZip(boolean useEncryption) throws Exception {
        // Given
        encrypted = useEncryption;
        setMaxArchiveSizeDefault();
        cache = createCache();

        cache.close();
        cache = createCache();

        // When: First record
        cache.add(createRequestRecordNormal());
        // Record with conflicting name
        cache.add(createRequestRecordNormal());

        // Then
        assertZip(expectedConflictingEntryNames(), getArchiveBytes());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void archivingShouldBeDeterministic(boolean useEncryption) throws Exception {
        // Given
        encrypted = useEncryption;
        cache = createCache();
        cache.close();

        var sha512 = DigestAlgorithm.ofName("SHA-512");
        final LinkingInfoBuilder builder1 = new LinkingInfoBuilder(sha512, new DigestEntry("deadbeef", "test"));
        cache = createCache(builder1);
        cache.add(createRequestRecordNormal());
        final byte[] bytes1 = getArchiveBytes();
        cache.close();

        id = 0;
        final LinkingInfoBuilder builder2 = new LinkingInfoBuilder(sha512, new DigestEntry("deadbeef", "test"));
        cache = createCache(builder2);
        cache.add(createRequestRecordNormal());
        final byte[] bytes2 = getArchiveBytes();
        cache.close();

        assertArrayEquals(bytes1, bytes2);
        assertEquals(builder1.getLastDigest(), builder2.getLastDigest());

        id = 0;
        final LinkingInfoBuilder builder3 = new LinkingInfoBuilder(sha512, new DigestEntry("", ""));
        cache = createCache(builder3);
        cache.add(createRequestRecordNormal());
        final byte[] bytes3 = getArchiveBytes();

        assertFalse(Arrays.equals(bytes1, bytes3));
        assertNotEquals(builder1.getLastDigest(), builder3.getLastDigest());
    }

    private byte[] getArchiveBytes() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final Path archive = cache.getArchiveFile();

        if (encrypted) {
            // Decrypt using BouncyCastle PGP
            try (InputStream encryptedInput = Files.newInputStream(archive);
                 OutputStream decryptedOutput = bos) {
                decryptor.decryptAndVerify(encryptedInput, decryptedOutput, keyManager.getSigningKeyPair());
            }
        } else {
            // Read unencrypted archive directly
            try (InputStream is = Files.newInputStream(archive)) {
                IOUtils.copy(is, bos);
            }
        }

        Files.delete(archive);
        return bos.toByteArray();
    }

    private void setMaxArchiveSizeSmall() {
        archiveMaxFilesize = ARCHIVE_SIZE_SMALL;
    }

    private void setMaxArchiveSizeMedium() {
        archiveMaxFilesize = ARCHIVE_SIZE_MEDIUM;
    }

    private void setMaxArchiveSizeDefault() {
        archiveMaxFilesize = DEFAULT_ARCHIVE_MAX_FILESIZE;
    }

    private LogArchiverExecutionProperties createExecutionProperties() {
        var archiveEncryption = new LogArchiverExecutionProperties.ArchiveEncryptionProperties(
                false,
                Optional.empty(),
                GroupingStrategy.NONE,
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
                archiveMaxFilesize,
                "build/tmp"
        );
    }

    private MessageRecord createRequestRecordNormal() throws Exception {
        return createMessageRecord(new AsicContainerParams(
                id++,
                "ID1",
                false,
                containerOfNormalSize(),
                LOG_TIME_REQUEST_NORMAL_LATEST));
    }

    private MessageRecord createRequestRecordTooLarge() throws Exception {
        return createMessageRecord(new AsicContainerParams(
                id++,
                "ID2",
                false,
                containerTooLarge(),
                LOG_TIME_REQUEST_LARGE_EARLIEST));
    }

    private MessageRecord createResponseRecordNormal() throws Exception {
        return createMessageRecord(new AsicContainerParams(
                id++,
                "ID3",
                true,
                containerOfNormalSize(),
                LOG_TIME_RESPONSE_NORMAL));
    }

    private MessageRecord createMessageRecord(AsicContainerParams params) throws Exception {
        MessageRecord messageRecord = mock(MessageRecord.class);
        when(messageRecord.getId()).thenReturn(params.id());
        when(messageRecord.getQueryId()).thenReturn(params.queryId());
        when(messageRecord.isResponse()).thenReturn(params.response());
        when(messageRecord.getTime()).thenReturn(params.creationTime());

        AsicContainer container = mock(AsicContainer.class);
        doAnswer(invocation -> {
            OutputStream os = (OutputStream) invocation.getArguments()[0];
            os.write(params.bytes());
            return null;
        }).when(container).write(any(OutputStream.class));

        when(messageRecord.toAsicContainer()).thenReturn(container);

        return messageRecord;
    }

    private byte[] containerOfNormalSize() {
        return "This one goes out to normal size container"
                .getBytes(StandardCharsets.UTF_8);
    }

    private byte[] containerTooLarge() {
        byte[] container = new byte[TOO_LARGE_CONTAINER_SIZE];

        for (int i = 0; i < TOO_LARGE_CONTAINER_SIZE; i++) {
            container[i] = (byte) (i % Byte.MAX_VALUE);
        }

        return container;
    }

    private Date normalRequestCreationTime() {
        return new Date(LOG_TIME_REQUEST_NORMAL_LATEST);
    }

    private Date largeRequestCreationTime() {
        return new Date(LOG_TIME_REQUEST_LARGE_EARLIEST);
    }

    private Date normalResponseCreationTime() {
        return new Date(LOG_TIME_RESPONSE_NORMAL);
    }

    private void assertZip(List<String> expectedEntryNames,
                           byte[] archiveBytes) throws IOException {
        if (archiveBytes == null || archiveBytes.length == 0) {
            fail("Bytes of zip archive must not be empty");
        }

        Set<String> names = new HashSet<>();
        InputStream archiveBytesInput = new ByteArrayInputStream(archiveBytes);

        try (ZipInputStream zip = new ZipInputStream(archiveBytesInput)) {
            for (int i = 0; i < expectedEntryNames.size(); i++) {
                ZipEntry entry = zip.getNextEntry();

                assertNotNull(entry, getZipEntryNotPresentMessage(i));
                assertThat(entry.getName(), isIn(expectedEntryNames));
                assertTrue(names.add(entry.getName()), "No duplicate names");
            }

            ZipEntry linkingInfoEntry = zip.getNextEntry();

            if (linkingInfoEntry == null) {
                throw new RuntimeException(
                        "There is no linking info present in the archive!");
            }

            assertEquals("linkinginfo", linkingInfoEntry.getName());

            assertNull(zip.getNextEntry(), "Zip entries must be taken by this point.");
        }
    }

    private Matcher<? super String> isIn(List<String> expectedEntryNames) {
        return new StringInListMatcher(expectedEntryNames);
    }

    private String getZipEntryNotPresentMessage(int orderNo) {
        return String.format("Zip entry number '%d' is supposed to be present, but is not", orderNo);
    }

    private List<String> expectedNormalSizeRequestEntryName() {
        return List.of(ENTRY_NAME_REQUEST_NORMAL);
    }

    private List<String> expectedLargeSizeRequestEntryName() {
        return List.of(ENTRY_NAME_REQUEST_LARGE);
    }

    private List<String> expectedNormalAndLargeRequestEntryNames() {
        return List.of(ENTRY_NAME_REQUEST_NORMAL, ENTRY_NAME_REQUEST_LARGE);
    }

    private List<String> expectedConflictingEntryNames() {
        return List.of(ENTRY_NAME_REQUEST_NORMAL, ENTRY_NAME_REQUEST_CONFLICTING);
    }

    private List<String> expectedNormalSizeResponseEntryName() {
        return List.of(ENTRY_NAME_RESPONSE_NORMAL);
    }

    private LinkingInfoBuilder mockLinkingInfoBuilder() {
        return new LinkingInfoBuilder(DigestAlgorithm.ofName("SHA-512"), new DigestEntry("", ""));
    }

    private LogArchiveCache createCache() {
        return createCache(mockLinkingInfoBuilder());
    }

    private LogArchiveCache createCache(LinkingInfoBuilder builder) {
        EncryptionConfig encryptionConfig;

        if (encrypted) {
            // Initialize BouncyCastle PGP encryption components
            PgpKeyGenerator keyGenerator = new PgpKeyGenerator();
            var keyInfo = keyGenerator.generate("Test Archive <test@archive.org>");

            // Create mock key provider
            var keyProvider = mock(PgpKeyProvider.class);
            when(keyProvider.getSigningSecretKey()).thenReturn(keyInfo.secretData());
            when(keyProvider.getEncryptionPublicKeys()).thenReturn(Optional.of(keyInfo.publicData()));

            // Initialize key manager
            keyManager = new PgpKeyManager(keyProvider);

            // Initialize encryption service
            var keyResolver = new PgpKeyResolver(keyManager);
            var encryptor = new StreamingPgpEncryptor();
            var encryptionService = new BouncyCastlePgpEncryptionService(keyManager, keyResolver, encryptor);

            // Initialize decryptor for reading encrypted archives
            decryptor = new StreamingPgpDecryptor();

            // Create encryption config that uses BouncyCastle
            encryptionConfig = new VaultServerEncryptionConfigProvider(keyManager, encryptionService)
                    .forGrouping(GroupingStrategy.NONE.forClient(null));
        } else {
            encryptionConfig = null;
        }

        return new LogArchiveCache(
                builder,
                encryptionConfig,
                Paths.get("build/tmp/"),
                createExecutionProperties()
        );
    }

    @RequiredArgsConstructor
    private static final class StringInListMatcher extends TypeSafeMatcher<String> {

        private final List<String> listOfElements;

        @Override
        protected boolean matchesSafely(String item) {
            return listOfElements.stream().anyMatch(item::startsWith);
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("Some of values in list: ").appendValue(listOfElements);
        }
    }

    private record AsicContainerParams(Long id, String queryId, boolean response, byte[] bytes, long creationTime) {
    }
}
