package ee.cyber.sdsb.commonui;

import java.io.IOException;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;

import ee.cyber.sdsb.common.ErrorCodes;
import ee.cyber.sdsb.common.ExpectedCodedException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OptionalPartsConfBehavior {
    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    @Test
    public void shouldGetValidationProgram() throws IOException {
        // Given
        String confDir = "src/test/resources/configuration-parts";
        OptionalPartsConf conf = new OptionalPartsConf(confDir);
        String partFile = "identifiermapping.xml";

        // When
        String actualValidationProgram = conf.getValidationProgram(partFile);

        // Then
        String expectedValidationProgram =
                "/usr/share/xroad/scripts/validate-identifiermapping.sh";

        assertEquals(expectedValidationProgram, actualValidationProgram);
    }

    @Test
    public void shouldGetContentIdentifier() throws IOException {
        // Given
        String confDir = "src/test/resources/configuration-parts";
        OptionalPartsConf conf = new OptionalPartsConf(confDir);
        String partFile = "identifiermapping.xml";

        // When
        String actualContentIdentifier = conf.getContentIdentifier(partFile);

        // Then
        String expectedContentIdentifier = "IDENTIFIERMAPPING";

        assertEquals(expectedContentIdentifier, actualContentIdentifier);
    }

    @Test
    public void shouldGetAllOptionalConfigurationParts() throws IOException {
        // Given
        String confDir = "src/test/resources/configuration-parts";
        OptionalPartsConf conf = new OptionalPartsConf(confDir);

        // When
        List<OptionalConfPart> actualOptionalParts = conf.getAllParts();

        // Then
        OptionalConfPart expectedFirstPart = new OptionalConfPart(
                "identifiermapping.xml", "IDENTIFIERMAPPING");

        OptionalConfPart expectedSecondPart = new OptionalConfPart(
                        "messageconverter.xml", "MESSAGECONVERTER");

        assertTrue(actualOptionalParts.contains(expectedFirstPart));
        assertTrue(actualOptionalParts.contains(expectedSecondPart));
    }

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
    @Test
    public void shouldNotAllowReservedFilenames() throws IOException {
        testMalformedConf(
                "src/test/resources/configuration-parts-RESERVED_FILE");
    }

    @Test
    public void shouldNotAllowReservedContentIds() throws IOException {
        testMalformedConf(
                "src/test/resources/configuration-parts-RESERVED_ID");
    }

    @Test
    public void shouldNotAllowDuplicateFilenames() throws IOException {
        testMalformedConf(
                "src/test/resources/configuration-parts-DUPLICATES");
    }

    private void testMalformedConf(String confDir) throws IOException {
        thrown.expectError(ErrorCodes.X_MALFORMED_OPTIONAL_PARTS_CONF);

        new OptionalPartsConf(confDir);
    }
}
