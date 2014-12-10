package ee.cyber.sdsb.commonui;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;

import ee.cyber.sdsb.common.ErrorCodes;
import ee.cyber.sdsb.common.ExpectedCodedException;

import static org.junit.Assert.assertEquals;

public class OptionalPartsConfBehavior {
    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    @Test
    public void shouldGetValidationProgram() throws IOException {
        // Given
        String confFile = "src/test/resources/configuration-parts.ini";
        OptionalPartsConf conf = new OptionalPartsConf(confFile);
        String partFile = "identifiermapping.xml";

        // When
        String actualValidationProgram = conf.getValidationProgram(partFile);

        // Then
        String expectedValidationProgram =
                "/usr/share/xroad/scripts/validate-identifiermapping.sh";

        assertEquals(expectedValidationProgram, actualValidationProgram);
    }

    @Test
    public void shouldGetContentIndentifier() throws IOException {
        // Given
        String confFile = "src/test/resources/configuration-parts.ini";
        OptionalPartsConf conf = new OptionalPartsConf(confFile);
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
        String confFile = "src/test/resources/configuration-parts.ini";
        OptionalPartsConf conf = new OptionalPartsConf(confFile);

        // When
        List<OptionalConfPart> actualOptionalParts = conf.getAllParts();

        // Then
        List<OptionalConfPart> expectedOptionalParts = Arrays.asList(
                new OptionalConfPart(
                        "identifiermapping.xml", "IDENTIFIERMAPPING"),
                new OptionalConfPart(
                        "messageconverter.xml", "MESSAGECONVERTER"));

        assertEquals(expectedOptionalParts, actualOptionalParts);
    }

    @Test
    public void shouldNotAllowReservedFilenames() throws IOException {
        testMalformedConf(
                "src/test/resources/configuration-parts-RESERVED_FILE.ini");
    }

    @Test
    public void shouldNotAllowReservedContentIds() throws IOException {
        testMalformedConf(
                "src/test/resources/configuration-parts-RESERVED_ID.ini");
    }

    @Test
    public void shouldNotAllowDuplicateFilenames() throws IOException {
        testMalformedConf(
                "src/test/resources/configuration-parts-DUPLICATES.ini");
    }

    private void testMalformedConf(String confFile) throws IOException {
        thrown.expectError(ErrorCodes.X_MALFORMED_OPTIONAL_PARTS_CONF);

        new OptionalPartsConf(confFile);
    }
}
