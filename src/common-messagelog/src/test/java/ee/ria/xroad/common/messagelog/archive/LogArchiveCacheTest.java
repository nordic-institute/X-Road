/**
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
package ee.ria.xroad.common.messagelog.archive;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.asic.AsicContainer;
import ee.ria.xroad.common.messagelog.MessageLogProperties;
import ee.ria.xroad.common.messagelog.MessageRecord;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

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
import java.util.List;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Actually tests inner class of LogArchive.
 */
@RunWith(Parameterized.class)
@Slf4j
public class LogArchiveCacheTest {
    private static final int TOO_LARGE_CONTAINER_SIZE = 10000;
    private static final int ARCHIVE_SIZE_SMALL = 50;
    private static final int ARCHIVE_SIZE_MEDIUM = 350;

    private static final String ENTRY_NAME_REQUEST_NORMAL =
            "ID1-request-RANDOM.asice";
    private static final String ENTRY_NAME_REQUEST_LARGE =
            "ID2-request-RANDOM.asice";
    private static final String ENTRY_NAME_RESPONSE_NORMAL =
            "ID3-response-RANDOM.asice";
    private static final String ENTRY_NAME_REQUEST_CONFLICTING =
            "ID1-request-RANDOM-0.asice";

    private static final long LOG_TIME_REQUEST_NORMAL_LATEST = 1428664947372L;
    private static final long LOG_TIME_REQUEST_LARGE_EARLIEST = 1428664660610L;
    private static final long LOG_TIME_RESPONSE_NORMAL = 1428664927050L;

    @Parameter(0)
    public boolean encrypted;

    private LogArchiveCache cache;

    @Parameters(name = "encrypted = {0}")
    public static Object[] params() {
        return new Object[] {Boolean.FALSE, Boolean.TRUE};
    }

    @Before
    public void setup() {
        System.setProperty(SystemProperties.TEMP_FILES_PATH, "build/tmp");

        if (encrypted) {
            Assume.assumeTrue(Files.isExecutable(Paths.get("/usr/bin/gpg")));
        }
        cache = createCache(getMockRandomGenerator());
        
    }

    @After
    public void tearDown() {
        if (cache != null) {
            cache.close();
        }
    }

    /**
     * Test to ensure one entry of normal size can be added successfully.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void addOneEntryOfNormalSize() throws Exception {
        // Given
        setMaxArchiveSizeDefault();

        // When;
        cache.add(createRequestRecordNormal());

        // Then
        assertFalse(
                "Should not rotate, as entry is small enough to fit in.",
                cache.isRotating());
        Date expectedCreationTime = normalRequestCreationTime();
        assertEquals(expectedCreationTime, cache.getStartTime());
        assertEquals(expectedCreationTime, cache.getEndTime());
        assertZip(expectedNormalSizeRequestEntryName(), getArchiveBytes());
    }

    /**
     * Test to ensure log archive is rotated if an entry is too large.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void rotateImmediatelyWhenOneEntryIsTooLarge() throws Exception {
        // Given
        setMaxArchiveSizeSmall();

        // When
        cache.add(createRequestRecordTooLarge());

        // Then
        assertTrue(
                "Entry is so large that rotation must take place",
                cache.isRotating());
        assertZip(expectedLargeSizeRequestEntryName(), getArchiveBytes());
    }

    /**
     * Test to ensure null message records are not allowed.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test(expected = IllegalArgumentException.class)
    public void doNotAllowNullMessageRecords() throws Exception {
        cache.add(null);
    }

    /**
     * Test to ensure the log archive is rotated inbetween log entry additions.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void addMultipleRecordsWithRotationMeanwhile() throws Exception {
        setMaxArchiveSizeMedium();

        // First record
        cache.add(createRequestRecordNormal());
        assertFalse("Step 1: no need to rotate yet.", cache.isRotating());

        // Second record
        cache.add(createRequestRecordTooLarge());

        assertTrue("Step 2: should be rotated.", cache.isRotating());
        assertEquals(largeRequestCreationTime(), cache.getStartTime());
        assertEquals(normalRequestCreationTime(), cache.getEndTime());
        assertZip(expectedNormalAndLargeRequestEntryNames(), getArchiveBytes());

        // Third record
        cache.add(createResponseRecordNormal());

        assertFalse("Step 3: new rotation.", cache.isRotating());
        assertEquals(normalResponseCreationTime(), cache.getStartTime());
        assertEquals(normalResponseCreationTime(), cache.getEndTime());
        assertZip(expectedNormalSizeResponseEntryName(), getArchiveBytes());
    }

    /**
     * Test to ensure name clash is avoided when fileName already exists in ZIP.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void avoidNameClashWhenFileWithSameNameIsAlreadyInSameZip()
            throws Exception {
        setMaxArchiveSizeDefault();

        // Create cache with more realistic random generator
        cache.close();
        cache = createCache(new TestRandomGenerator());

        // First record
        cache.add(createRequestRecordNormal());
        // Record with conflicting name
        cache.add(createRequestRecordNormal());
        assertZip(expectedConflictingEntryNames(), getArchiveBytes());
    }

    private byte[] getArchiveBytes() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final Path archive = cache.getArchiveFile();
        final InputStream is;

        if (encrypted) {
            is = new GPGInputStream(Paths.get("build/gpg"), archive);
        } else {
            is = Files.newInputStream(archive);
        }

        try (InputStream src = is) {
            IOUtils.copy(src, bos);
        }

        if (encrypted) {
            GPGInputStream gis = ((GPGInputStream) is);
            assertEquals(0, gis.getExitCode());
            assertEquals(2, gis.getStatus().stream()
                    .filter(it -> it.contains("DECRYPTION_OKAY") || it.contains("GOODSIG"))
                    .count());
        }

        Files.delete(archive);
        return bos.toByteArray();
    }

    private void setMaxArchiveSizeSmall() {
        setMaxArchiveSize(ARCHIVE_SIZE_SMALL);
    }

    private void setMaxArchiveSizeMedium() {
        setMaxArchiveSize(ARCHIVE_SIZE_MEDIUM);
    }

    private void setMaxArchiveSizeDefault() {
        setMaxArchiveSize(null);
    }

    private void setMaxArchiveSize(Integer value) {
        String propertyValue = value != null ? Integer.toString(value) : "";
        System.setProperty(
                MessageLogProperties.ARCHIVE_MAX_FILESIZE, propertyValue);
    }

    private MessageRecord createRequestRecordNormal() throws Exception {
        AsicContainerParams containerParams = new AsicContainerParams(
                "ID1",
                false,
                containerOfNormalSize(),
                LOG_TIME_REQUEST_NORMAL_LATEST);

        return createMessageRecord(containerParams);
    }

    private MessageRecord createRequestRecordTooLarge() throws Exception {
        AsicContainerParams containerParams = new AsicContainerParams(
                "ID2",
                false,
                containerTooLarge(),
                LOG_TIME_REQUEST_LARGE_EARLIEST);

        return createMessageRecord(containerParams);
    }

    private MessageRecord createResponseRecordNormal() throws Exception {
        AsicContainerParams containerParams = new AsicContainerParams(
                "ID3", true, containerOfNormalSize(), LOG_TIME_RESPONSE_NORMAL);

        return createMessageRecord(containerParams);
    }

    private MessageRecord createMessageRecord(
            AsicContainerParams params) throws Exception {
        MessageRecord record = mock(MessageRecord.class);
        when(record.getQueryId()).thenReturn(params.getId());
        when(record.isResponse()).thenReturn(params.isResponse());
        when(record.getTime()).thenReturn(params.getCreationTime());

        AsicContainer container = mock(AsicContainer.class);
        doAnswer(invocation -> {
            OutputStream os = (OutputStream) invocation.getArguments()[0];
            os.write(params.getBytes());
            return null;
        }).when(container).write(any(OutputStream.class));

        when(record.toAsicContainer()).thenReturn(container);

        return record;
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

        InputStream archiveBytesInput = new ByteArrayInputStream(archiveBytes);

        try (ZipInputStream zip = new ZipInputStream(archiveBytesInput)) {
            for (int i = 0; i < expectedEntryNames.size(); i++) {
                ZipEntry entry = zip.getNextEntry();

                assertNotNull(getZipEntryNotPresentMessage(i), entry);
                assertThat(entry.getName(), isIn(expectedEntryNames));
            }

            ZipEntry linkingInfoEntry = zip.getNextEntry();

            if (linkingInfoEntry == null) {
                throw new RuntimeException(
                        "There is no linking info present in the archive!");
            }

            assertEquals("linkinginfo", linkingInfoEntry.getName());

            assertNull(
                    "Zip entries must be taken by this point.",
                    zip.getNextEntry());
        }
    }

    private Matcher<? super String> isIn(List<String> expectedEntryNames) {
        return new StringInListMatcher(expectedEntryNames);
    }

    private String getZipEntryNotPresentMessage(int orderNo) {
        return String.format(
                "Zip entry number '%d' is supposed to be present, but is not",
                orderNo);
    }

    private List<String> expectedNormalSizeRequestEntryName() {
        // Format: ID-request-Z.asice
        return Arrays.asList(ENTRY_NAME_REQUEST_NORMAL);
    }

    private List<String> expectedLargeSizeRequestEntryName() {
        // Format: ID-request-Z.asice
        return Arrays.asList(ENTRY_NAME_REQUEST_LARGE);
    }

    private List<String> expectedNormalAndLargeRequestEntryNames() {
        // Format: ID-request-Z.asice
        return Arrays.asList(
                ENTRY_NAME_REQUEST_NORMAL, ENTRY_NAME_REQUEST_LARGE);
    }

    private List<String> expectedConflictingEntryNames() {
        // Format: ID-request-Z.asice
        return Arrays.asList(
                ENTRY_NAME_REQUEST_NORMAL, ENTRY_NAME_REQUEST_CONFLICTING);
    }

    private List<String> expectedNormalSizeResponseEntryName() {
        // Format: ID-request-Z.asice
        return Arrays.asList(ENTRY_NAME_RESPONSE_NORMAL);
    }

    @SuppressWarnings("unchecked")
    private Supplier<String> getMockRandomGenerator() {
        Supplier<String> generator = mock(Supplier.class);

        when(generator.get()).thenReturn("RANDOM");

        return generator;
    }

    private LinkingInfoBuilder mockLinkingInfoBuilder() {
        LinkingInfoBuilder builder = mock(LinkingInfoBuilder.class);

        when(builder.build()).thenReturn("DUMMY".getBytes());

        return builder;
    }

    private LogArchiveCache createCache(Supplier<String> randomGenerator) {
        return new LogArchiveCache(
                randomGenerator,
                mockLinkingInfoBuilder(),
                encrypted ? new EncryptionConfig(true, Paths.get("build/gpg"), null)
                        : EncryptionConfig.DISABLED,
                Paths.get("build/tmp/")
        );
    }

    @RequiredArgsConstructor
    private static class StringInListMatcher extends TypeSafeMatcher<String> {

        private final List<String> listOfElements;

        @Override
        protected boolean matchesSafely(String item) {
            return listOfElements.contains(item);
        }

        @Override
        public void describeTo(Description description) {
            description
                    .appendText("Some of values in list: ")
                    .appendValue(listOfElements);
        }
    }

    @Value
    private static class AsicContainerParams {
        private String id;
        private boolean response;
        private byte[] bytes;
        private long creationTime;
    }

    /**
     * Generates different random only on certain invocation.
     */
    private static class TestRandomGenerator implements Supplier<String> {
        private static final int DIFFERENT_ON_INVOCATION = 3;
        int invocations = 0;

        @Override
        public String get() {
            invocations++;
            String result = "RANDOM";

            if (invocations == DIFFERENT_ON_INVOCATION) {
                result = String.format("%s-0", result);
            }

            return result;
        }
    }
}
