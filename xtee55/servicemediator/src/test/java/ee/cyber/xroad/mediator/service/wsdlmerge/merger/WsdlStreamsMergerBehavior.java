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
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.xroad.mediator.service.wsdlmerge.parser.WSDLParser;

import static org.junit.Assert.assertEquals;

/**
 * Tests cooperation of {@link WSDLParser} and {@link WSDLMerger}. Input WSDL-s
 * that are processable by MISP are turned into output WSDL-s that MISP also
 * can process.
 */
public class WsdlStreamsMergerBehavior {

    private static final Logger LOG = LoggerFactory
            .getLogger(WsdlStreamsMergerBehavior.class);

    @Test
    public void shouldComposeWsdlThatMispCanProcess() throws Exception {

        InputStream wsdlInput1 =
                getWsdlInputStream("mergeable_1.wsdl");
        InputStream wsdlInput2 =
                getWsdlInputStream("mergeable_2.wsdl");

        WSDLStreamsMerger streamsMerger =
                new WSDLStreamsMerger(
                        Arrays.asList(wsdlInput1, wsdlInput2), "uusandmekogu");

        String wsdlAsString =
                IOUtils.toString(streamsMerger.getMergedWsdlAsStream());

        LOG.info("Output WSDL:\n{}" + wsdlAsString);

        String outputWsdlFile = "build/merged.wsdl";

        LOG.info("Output WSDL is going to be written into file '{}'",
                outputWsdlFile);

        FileUtils.writeStringToFile(
                new File(outputWsdlFile),
                wsdlAsString,
                StandardCharsets.UTF_8);

        verifyWsdl(wsdlAsString);
    }

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

    private void verifyWsdl(String wsdlAsString) throws IOException {
        List<String> rawExpectedWsdlLines = FileUtils.readLines(
                new File(getWsdlFilePath("merged-expected.wsdl")),
                StandardCharsets.UTF_8);

        List<String> rawActualWsdlLines = Arrays.asList(
                wsdlAsString.split("\n"));

        List<String> expectedWsdlLines = cleanWsdlOutput(rawExpectedWsdlLines);
        List<String> actualWsdlLines = cleanWsdlOutput(rawActualWsdlLines);

        assertEquals(expectedWsdlLines.size(), actualWsdlLines.size());

        for (int i = 0; i < expectedWsdlLines.size(); i++) {
            LOG.info("Asserting line number '{}'...", i);
            assertEquals(expectedWsdlLines.get(i), actualWsdlLines.get(i));
        }

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
