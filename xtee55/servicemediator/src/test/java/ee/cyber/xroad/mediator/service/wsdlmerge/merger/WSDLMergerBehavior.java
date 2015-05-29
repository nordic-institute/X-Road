package ee.cyber.xroad.mediator.service.wsdlmerge.merger;

import java.io.IOException;
import java.util.Arrays;

import org.junit.Test;

import ee.cyber.xroad.mediator.service.wsdlmerge.TestNS;
import ee.cyber.xroad.mediator.service.wsdlmerge.structure.WSDL;

import static org.junit.Assert.assertEquals;

/**
 * Tests functionality of WSDL merger.
 */
public class WSDLMergerBehavior {
    private static final String DB_V5_NAME = "uusandmekogu";

    /**
     * Testing normal case of merging two WSDL-s.
     *
     * @throws Exception thrown when merging fails.
     */
    @Test
    public void shouldMergeTwoWsdls() throws Exception {
        // Given
        WSDL wsdlFirst = getDoclitWsdlContainingGetRandom();
        WSDL wsdlSecond = getDoclitWsdlContainingSmallAttachment();

        // When
        WSDLMerger merger = new WSDLMerger(
                Arrays.asList(wsdlFirst, wsdlSecond), DB_V5_NAME);

        // Then
        WSDL mergedWsdl = merger.getMergedWsdl();

        assertEquals(getExpectedSimpleWsdl(), mergedWsdl);
    }

    /**
     * Tests situation when two WSDL-s with exactly the same service are trying
     * to be merged.
     *
     * @throws InvalidWSDLCombinationException thrown if successful.
     */
    @Test(expected = InvalidWSDLCombinationException.class)
    public void shouldThrowErrorWhenMultipleServicesWithSameNameAndVersion()
            throws InvalidWSDLCombinationException {
        // Given
        WSDL wsdlFirst = getDoclitWsdlContainingGetRandom();
        WSDL wsdlSecond = getDoclitWsdlContainingGetRandom();

        // When/then
        new WSDLMerger(Arrays.asList(wsdlFirst, wsdlSecond), DB_V5_NAME);
    }

    /**
     * Tests situation when WSDL-s with different target namespaces are trying
     * to be merged.
     *
     * @throws InvalidWSDLCombinationException thrown if successful.
     */
    @Test(expected = InvalidWSDLCombinationException.class)
    public void shouldThrowErrorWhenDifferentXrdNamespaces()
            throws InvalidWSDLCombinationException {
        // Given
        WSDL wsdlFirst = getDoclitWsdlContainingGetRandom();
        WSDL wsdlSecond = getWsdlWithDifferentXrdNamespace();

        // When/then
        new WSDLMerger(Arrays.asList(wsdlFirst, wsdlSecond), DB_V5_NAME);
    }

    /**
     * Tests situation when WSDL-s containing services with same name, but
     * different version are merged.
     *
     * @throws IOException thrown if merging fails.
     */
    @Test
    public void shouldMergeTwoWsdlsWithDifferentVersionsOfSameService()
            throws IOException {
        // Given
        WSDL wsdlFirst = getDoclitWsdlContainingGetRandom();
        WSDL wsdlSecond = getWsdlContainingGetRandomNewerVersion();

        // When
        WSDLMerger merger = new WSDLMerger(
                Arrays.asList(wsdlFirst, wsdlSecond), DB_V5_NAME);

        // Then
        WSDL mergedWsdl = merger.getMergedWsdl();

        assertEquals(getExpectedReducedWsdl(), mergedWsdl);
    }

    // -- Methods for getting WSDL - start ---

    private WSDL getDoclitWsdlContainingGetRandom() {
        return getWsdlContainingGetRandom();
    }

    private WSDL getDoclitWsdlContainingSmallAttachment() {
        return new MockWSDLCreator(
                "smallAttachment", "v1", "", TestNS.XROAD_NS)
                .getWSDL();
    }

    private WSDL getWsdlContainingGetRandomNewerVersion() {
        return new MockWSDLCreator(
                "getRandom", "v2", "_2", TestNS.XROAD_NS)
                .getWSDL();
    }

    private WSDL getWsdlContainingGetRandom() {
        return new MockWSDLCreator(
                "getRandom", "v1", "", TestNS.XROAD_NS)
                .getWSDL();
    }

    private WSDL getWsdlWithDifferentXrdNamespace() {
        return new MockWSDLCreator(
                "smallAttachment",
                "v1",
                "",
                "http://www.xrd-different.xom").getWSDL();
    }

    private WSDL getExpectedSimpleWsdl() {
        return new MockWSDLCreator(
                Arrays.asList("getRandom", "smallAttachment"),
                "v1",
                "",
                TestNS.XROAD_NS,
                TestNS.XRDDL_NEW_TNS,
                DB_V5_NAME).getWSDL();
    }

    private WSDL getExpectedReducedWsdl() {
        return new MockWSDLCreator(
                Arrays.asList("getRandom"),
                "v2",
                "_2",
                TestNS.XROAD_NS,
                TestNS.XRDDL_NEW_TNS,
                DB_V5_NAME).getWSDL();
    }

    // -- Methods for getting WSDL - end ---

}
