package ee.cyber.xroad.mediator.service.wsdlmerge.merger;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import ee.cyber.xroad.mediator.service.wsdlmerge.structure.WSDL;

import static org.junit.Assert.assertEquals;

public class WSDLMergerBehavior {
    private static final String RESOURCES_DIR = "src/test/resources/";

    // @Test TODO
    public void shouldMergeTwoWsdls() throws IOException {
        // Given
        WSDL wsdlFirst = getWsdlContainingGetRandom();
        WSDL wsdlSecond = getWsdlContainingSmallAttachment();

        // When
        WSDLMerger merger = new WSDLMerger(Arrays.asList(wsdlFirst, wsdlSecond));

        // Then
        String mergedWsdl = IOUtils.toString(merger.getMergedWsdlAsStream());
        String expectedWsdl = FileUtils.readFileToString(
                new File(RESOURCES_DIR + "xrddl-merged-simple.wsdl"));

        assertEquals(expectedWsdl, mergedWsdl);
    }

    private WSDL getWsdlContainingGetRandom() {
        // TODO Auto-generated method stub
        return null;
    }

    private WSDL getWsdlContainingSmallAttachment() {
        // TODO Auto-generated method stub
        return null;
    }
}
