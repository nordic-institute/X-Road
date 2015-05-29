package ee.cyber.xroad.mediator.service;

import java.io.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import javax.wsdl.Definition;
import javax.wsdl.WSDLException;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLReader;
import javax.wsdl.xml.WSDLWriter;

import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.xml.sax.InputSource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests to verify correct dummy WSDL creator behavior.
 */
public class DummyWSDLCreatorTest {

    /**
     * Test to ensure a dummy WSDL is correctly created and parsed from a string.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void createAndParseWSDL() throws Exception {
        String url = "http://localhost:1234";
        List<String> methods = Arrays.asList("aa.foo", "aa.bar", "aa.baz");

        Definition wsdl = new DummyWSDLCreator(url).create(methods);

        StringWriter sw = new StringWriter();
        WSDLFactory wsdlFactory =
                WSDLFactory.newInstance("com.ibm.wsdl.factory.WSDLFactoryImpl");
        wsdlFactory.newWSDLWriter().writeWSDL(wsdl, sw);

        Collection<WSDLParser.ServiceInfo> actualServiceInfo =
                WSDLParser.parseWSDL(
                        new ByteArrayInputStream(sw.toString().getBytes()));
        for (WSDLParser.ServiceInfo service : actualServiceInfo) {
            assertTrue(methods.contains("aa." + service.name));
            assertEquals(url, service.url);
        }
    }

    /**
     * Test to ensure a well-formed WSDL is correctly created.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void createWellFormedWSDL() throws Exception {
        String url = "http://localhost:1234";
        List<String> methods = Arrays.asList("aa.foo", "aa.bar", "aa.baz");
        String writableWsdlPath = "build/created-well-formed.wsdl";

        Definition wsdl = new DummyWSDLCreator(url).create(methods);

        writeWsdlIntoFile(wsdl, writableWsdlPath);

        assertWellFormed(writableWsdlPath);
    }

    void writeWsdlIntoFile(
            Definition wsdlDefinition, String outputPath)
            throws IOException, WSDLException {
        try (Writer fileWriter = new FileWriter(new File(outputPath))) {
            WSDLWriter wsdlWriter = WSDLFactory.newInstance().newWSDLWriter();

            wsdlWriter.writeWSDL(wsdlDefinition, fileWriter);
        }
    }

    private void assertWellFormed(String writableWsdlPath) throws Exception {
        try (InputStream wsdlInputStream = new FileInputStream(writableWsdlPath)) {
            InputSource inputSource = new InputSource(wsdlInputStream);
            WSDLReader wsdlReader = WSDLFactory.newInstance().newWSDLReader();
            wsdlReader.setFeature("javax.wsdl.importDocuments", true);

            assertContent(writableWsdlPath);
            wsdlReader.readWSDL(null, inputSource);
        }
    }

    /**
     * Asserts if the content of resulting WSDL is correct.
     *
     * XXX: current implementation is quite naive - it just compares file lines.
     * But reference file (src/test/resources/dummy-valid.wsdl) is successfully
     * validated against Eclipse validator. Alternatively, calling some
     * validator (for example Eclipse validator) programmatically may be more
     * bulletproof approach.
     *
     * @param writableWsdlPath - path where output WSDL is written to
     * @throws IOException - thrown when output or comparison file is
     * unreachable.
     */
    private void assertContent(String writableWsdlPath) throws IOException {
        List<String> expectedContent = readFileLines(writableWsdlPath);
        List<String> actualContent =
                readFileLines("src/test/resources/dummy-valid.wsdl");

        assertEquals(expectedContent, actualContent);
    }

    private List<String> readFileLines(String filePath) throws IOException {
        List<String> rawLines = FileUtils.readLines(new File(filePath));
        List<String> result = rawLines.stream()
                .map(String::trim)
                .collect(Collectors.toList());

        return result;
    }
}
