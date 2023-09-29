/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.commonui;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.ExpectedCodedException;

import org.hamcrest.CoreMatchers;
import org.hamcrest.core.AnyOf;
import org.hamcrest.core.StringContains;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Tests to verify correct optional configuration parts behavior.
 */
public class OptionalPartsConfBehavior {
    private static final String CONF_DIR = "src/test/resources/configuration-parts";
    private static final String MESSAGE_CONVERTER_FILE = CONF_DIR + File.separator + "message-converter.ini";

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    /**
     * Test to ensure test configuration part content identifier can be read.
     * @throws IOException in case optional parts directory cannot be read
     */
    @Test
    public void shouldGetContentIdentifier() throws IOException {
        // Given
        String confDir = "src/test/resources/configuration-parts";
        OptionalPartsConf conf = new OptionalPartsConf(confDir);
        String partFile = "test-configuration-part.xml";

        // When
        String actualContentIdentifier = conf.getContentIdentifier(partFile);

        // Then
        String expectedContentIdentifier = "TEST-CONFIGURATION-PART";

        assertEquals(expectedContentIdentifier, actualContentIdentifier);
    }

    @Test
    public void shouldGetPartFileName() throws IOException {
        String confDir = "src/test/resources/configuration-parts";
        OptionalPartsConf conf = new OptionalPartsConf(confDir);

        assertEquals("messageconverter.xml", conf.getPartFileName("MESSAGECONVERTER"));
        assertEquals("test-configuration-part.xml", conf.getPartFileName("TEST-CONFIGURATION-PART"));
    }

    @Test
    public void shouldGetPartFileNameThrowException() throws IOException {
        String confDir = "src/test/resources/configuration-parts";
        OptionalPartsConf conf = new OptionalPartsConf(confDir);

        assertThrows(CodedException.class, () -> conf.getPartFileName("NOT-EXISTING"));
    }

    /**
     * Test to ensure all configuration parts in the directory can be read.
     * @throws IOException in case optional parts directory cannot be read
     */
    @Test
    public void shouldGetAllOptionalConfigurationParts() throws IOException {
        // Given
        OptionalPartsConf conf = new OptionalPartsConf(CONF_DIR);

        // When
        List<OptionalConfPart> actualOptionalParts = conf.getAllParts();

        // Then
        OptionalConfPart expectedFirstPart = new OptionalConfPart("test-configuration-part.xml",
                "TEST-CONFIGURATION-PART");

        OptionalConfPart expectedSecondPart = new OptionalConfPart("messageconverter.xml", "MESSAGECONVERTER");

        assertEquals(2, actualOptionalParts.size());
        assertTrue(actualOptionalParts.contains(expectedFirstPart));
        assertTrue(actualOptionalParts.contains(expectedSecondPart));
    }

    /**
     * Test to ensure errors are added when cannot read the configuration parts
     * file.
     * @throws IOException in case optional parts directory cannot be read
     */
    @Test
    public void shouldAddErrorsIfCannotReadConfigurationPartFile() throws IOException {
        // Given
        corruptPermissions();

        // When
        OptionalPartsConf conf = new OptionalPartsConf(CONF_DIR);

        // Then
        assertEquals(1, conf.getAllParts().size());

        List<String> errors = conf.getErrors();

        assertEquals(1, errors.size());
        assertThat(errors.get(0), containsPermissionDenied());

        restorePermissions();
    }

    /**
     * Test to ensure the optional parts list is empty if directory does not exist.
     * @throws IOException in case optional parts directory cannot be read
     */
    @Test
    public void shouldReturnEmptyListWhenNoConfDirectory() throws IOException {
        // Given
        String confDir = "src/test/resources/configuration-parts-NONEXISTENT";
        OptionalPartsConf conf = new OptionalPartsConf(confDir);

        // When
        List<OptionalConfPart> actualOptionalParts = conf.getAllParts();

        // Then
        assertEquals(0, actualOptionalParts.size());
    }

    /**
     * Test to ensure malformed files are skipped.
     * @throws IOException in case optional parts directory cannot be read
     */
    @Test
    public void shouldSkipFileWhenMalformed() throws IOException {
        // Given
        String confDir = "src/test/resources/configuration-parts-MALFORMED_FILE";
        OptionalPartsConf conf = new OptionalPartsConf(confDir);

        // When
        List<OptionalConfPart> actualOptionalParts = conf.getAllParts();

        // Then
        assertEquals(0, actualOptionalParts.size());
    }

    /**
     * Test to ensure reserved filenames are not allowed in configuration parts.
     * @throws IOException in case optional parts directory cannot be read
     */
    @Test
    public void shouldNotAllowReservedFilenames() throws IOException {
        testMalformedConf("src/test/resources/configuration-parts-RESERVED_FILE");
    }

    /**
     * Test to ensure reserved content IDs are not allowed in configuration parts.
     * @throws IOException in case optional parts directory cannot be read
     */
    @Test
    public void shouldNotAllowReservedContentIds() throws IOException {
        testMalformedConf("src/test/resources/configuration-parts-RESERVED_ID");
    }

    /**
     * Test to ensure duplicate filenames are not allowed in configuration parts.
     * @throws IOException in case optional parts directory cannot be read
     */
    @Test
    public void shouldNotAllowDuplicateFilenames() throws IOException {
        testMalformedConf("src/test/resources/configuration-parts-DUPLICATES");
    }

    private void testMalformedConf(String confDir) throws IOException {
        thrown.expectError(ErrorCodes.X_MALFORMED_OPTIONAL_PARTS_CONF);

        new OptionalPartsConf(confDir);
    }

    private AnyOf<String> containsPermissionDenied() {
        return CoreMatchers.anyOf(new StringContains("(Permission denied)"),
                new StringContains("(Operation not permitted)"));
    }

    private void corruptPermissions() throws IOException {
        File file = new File(MESSAGE_CONVERTER_FILE);

        Files.setPosixFilePermissions(file.toPath(), new HashSet<>());
    }

    private void restorePermissions() throws IOException {
        File file = new File(MESSAGE_CONVERTER_FILE);

        HashSet<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.GROUP_READ);
        perms.add(PosixFilePermission.OTHERS_READ);

        perms.add(PosixFilePermission.OWNER_WRITE);
        perms.add(PosixFilePermission.GROUP_WRITE);

        Files.setPosixFilePermissions(file.toPath(), perms);
    }
}
