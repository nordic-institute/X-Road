package ee.cyber.xroad.mediator.service.wsdlmerge.merger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.ExpectedCodedException;

import static org.junit.Assert.assertEquals;

/**
 * Tests cooperation of {@link WSDLParser} and {@link WSDLMerger}. Input WSDL-s
 * that are processable by MISP are turned into output WSDL-s that MISP also
 * can process.
 */
public class WSDLStreamsMergerBehavior {

    private static final Logger LOG = LoggerFactory
            .getLogger(WSDLStreamsMergerBehavior.class);

    @Rule
    public ExpectedCodedException thrown =
            ExpectedCodedException.none();

    /**
     * Tests if WSDL can be created that is processable to MISP.
     *
     * @throws Exception thrown when creating WSDL for MISP fails.
     */
    @Test
    public void shouldComposeWsdlThatMispCanProcess() throws Exception {

        InputStream wsdlInput1 =
                getWsdlInputStream("mergeable_1.wsdl");
        InputStream wsdlInput2 =
                getWsdlInputStream("mergeable_2.wsdl");

        WSDLStreamsMerger streamsMerger =
                new WSDLStreamsMerger(
                        Arrays.asList(wsdlInput1, wsdlInput2), "uusandmekogu");

        List<String> wsdlLines = IOUtils.readLines(
                streamsMerger.getMergedWsdlAsStream(), StandardCharsets.UTF_8);

        String wsdlAsString = StringUtils.join(wsdlLines, "\n");

        LOG.info("Output WSDL:\n{}" + wsdlAsString);

        String outputWsdlFile = "build/merged.wsdl";

        LOG.info("Output WSDL is going to be written into file '{}'",
                outputWsdlFile);

        FileUtils.writeStringToFile(
                new File(outputWsdlFile),
                wsdlAsString,
                StandardCharsets.UTF_8);

        verifyWsdl(wsdlLines);
    }

    /**
     * Tests restriction that WSDL-s with same schema element name cannot be
     * merged.
     *
     * @throws Exception indicates success if InvalidWSDLCombinationException
     * is thrown.
     */
    @Test(expected = InvalidWSDLCombinationException.class)
    public void shouldNotAllowToMergeWsdlsWithConflictingSchemaElementNames()
            throws Exception {
        InputStream wsdlInput1 =
                getWsdlInputStream("mergeable_1.wsdl");
        InputStream wsdlInput2 =
                getWsdlInputStream("mergeable_3_CONFLICTING.wsdl");

        new WSDLStreamsMerger(
                Arrays.asList(wsdlInput1, wsdlInput2), "uusandmekogu");
    }

    /**
     * Tests that merging WSDL-s with imported Schemas is not allowed.
     *
     * @throws Exception indicates success if CodedException with error code
     * X_IO_ERROR is thrown.
     */
    @Test
    public void shouldBanMergingWsdlsWithImportedSchemas()
            throws Exception {
        thrown.expectError(ErrorCodes.X_IO_ERROR);

        InputStream wsdlInput1 =
                getWsdlInputStream("mergeable_1.wsdl");
        InputStream wsdlInput2 =
                getWsdlInputStream("mergeable_4_UNREACHABLE_IMPORT.wsdl");

        new WSDLStreamsMerger(
                Arrays.asList(wsdlInput1, wsdlInput2), "uusandmekogu");
    }

    private void verifyWsdl(List<String> rawActualWsdlLines) throws IOException {
        List<String> rawExpectedWsdlLines = FileUtils.readLines(
                new File(getWsdlFilePath("merged-expected.wsdl")),
                StandardCharsets.UTF_8);

        List<String> expectedWsdlLines = cleanWsdlOutput(rawExpectedWsdlLines);
        List<String> actualWsdlLines = cleanWsdlOutput(rawActualWsdlLines);

        assertEquals(expectedWsdlLines.size(), actualWsdlLines.size());

        expectedWsdlLines.forEach(expectedLine -> {
            if (actualWsdlLines.contains(expectedLine)) {
                return;
            }

            throw new RuntimeException("Line '" + expectedLine
                    + "' was expected in WSDL, but it did not appear.");
        });

        LOG.info("WSDL verified successfully.");
    }

    private List<String> cleanWsdlOutput(List<String> rawInput) {
        List<String> result = new ArrayList<>();

        for (String eachLine : rawInput) {
            if (StringUtils.isBlank(eachLine)) {
                continue;
            }

            result.add(eachLine.trim());
        }
        return result;
    }

    private InputStream getWsdlInputStream(String wsdlName)
            throws FileNotFoundException {
        return new FileInputStream(getWsdlFilePath(wsdlName));
    }

    private String getWsdlFilePath(String wsdlName) {
        return "src/test/resources/" + wsdlName;
    }
}
