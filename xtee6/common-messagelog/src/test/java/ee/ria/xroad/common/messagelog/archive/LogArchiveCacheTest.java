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
package ee.ria.xroad.common.messagelog.archive;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.asic.AsicContainer;
import ee.ria.xroad.common.messagelog.MessageLogProperties;
import ee.ria.xroad.common.messagelog.MessageRecord;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Actually tests inner class of LogArchive.
 */
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

    private LogArchiveCache cache = new LogArchiveCache(
            getMockRandomGenerator(), mockLinkingInfoBuilder());

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    /**
     * Preparations for testing log archive cache.
     */
    @Before
    public void beforeTest() {
        System.setProperty(SystemProperties.TEMP_FILES_PATH, "build/tmp/");
    }

    /**
     * Test to ensure one entry of normal size can be added successfully.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void addOneEntryOfNormalSize() throws Exception {
        // Given
        setMaxArchiveSizeDefault();

        // When;
        cache.add(createRequestRecordNormal());

        // Then
        // TODO Get bytes from temp file from now on!
        assertZip(expectedNormalSizeRequestEntryName(), getArchiveBytes());
        assertFalse(
                "Should not rotate, as entry is small enough to fit in.",
                cache.isRotating());

        Date expectedCreationTime = normalRequestCreationTime();
        assertEquals(expectedCreationTime, cache.getStartTime());
        assertEquals(expectedCreationTime, cache.getEndTime());
    }

    /**
     * Test to ensure log archive is rotated if an entry is too large.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void rotateImmediatelyWhenOneEntryIsTooLarge() throws Exception {
        // Given
        setMaxArchiveSizeSmall();

        // When
        cache.add(createRequestRecordTooLarge());

        // Then
        assertZip(expectedLargeSizeRequestEntryName(), getArchiveBytes());
        assertTrue(
                "Entry is so large that rotation must take place",
                cache.isRotating());
    }

    /**
     * Test to ensure null message records are not allowed.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void doNotAllowNullMessageRecords() throws Exception {
        thrown.expect(IllegalArgumentException.class);

        cache.add(null);

        thrown.expectMessage("Message record to be archived must not be null");
    }

    /**
     * Test to ensure the log archive is rotated inbetween log entry additions.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void addMultipleRecordsWithRotationMeanwhile() throws Exception {
        setMaxArchiveSizeMedium();

        // First record
        cache.add(createRequestRecordNormal());

        assertZip(expectedNormalSizeRequestEntryName(), getArchiveBytes());
        assertFalse("Step 1: no need to rotate yet.", cache.isRotating());

        // Second record
        cache.add(createRequestRecordTooLarge());

        assertZip(expectedNormalAndLargeRequestEntryNames(), getArchiveBytes());
        assertTrue("Step 2: should be rotated.", cache.isRotating());
        assertEquals(largeRequestCreationTime(), cache.getStartTime());
        assertEquals(normalRequestCreationTime(), cache.getEndTime());

        // Third record
        cache.add(createResponseRecordNormal());

        assertZip(expectedNormalSizeResponseEntryName(), getArchiveBytes());
        assertFalse("Step 3: new rotation.", cache.isRotating());
        assertEquals(normalResponseCreationTime(), cache.getStartTime());
        assertEquals(normalResponseCreationTime(), cache.getEndTime());
    }

    /**
     * Test to ensure name clash is avoided when fileName already exists in ZIP.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void avoidNameClashWhenFileWithSameNameIsAlreadyInSameZip()
            throws Exception {
        setMaxArchiveSizeDefault();
        createCacheWithMoreRealisticRandom();

        // First record
        cache.add(createRequestRecordNormal());
        assertZip(expectedNormalSizeRequestEntryName(), getArchiveBytes());

        // Record with conflicting name
        cache.add(createRequestRecordNormal());
        assertZip(expectedConflictingEntryNames(), getArchiveBytes());
    }

    private byte[] getArchiveBytes() throws IOException {
        return IOUtils.toByteArray(cache.getArchiveFile());
    }

    private void createCacheWithMoreRealisticRandom() {
        cache = new LogArchiveCache(
                new TestRandomGenerator(), mockLinkingInfoBuilder());
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
        when(container.getBytes()).thenReturn(params.getBytes());

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

    private void assertZip(
            List<String> expectedEntryNames, byte[] archiveBytes) throws IOException {
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
