package ee.cyber.xroad.mediator.service.wsdlmerge.merger;

import java.io.IOException;
import java.util.Arrays;

import org.junit.Test;

import ee.cyber.xroad.mediator.service.wsdlmerge.TestNS;
import ee.cyber.xroad.mediator.service.wsdlmerge.structure.WSDL;

import static org.junit.Assert.assertEquals;

public class WSDLMergerBehavior {
    private static final String DB_V5_NAME = "uusandmekogu";

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

    @Test(expected = InvalidWSDLCombinationException.class)
    public void shouldThrowErrorWhenMultipleServicesWithSameNameAndVersion()
            throws InvalidWSDLCombinationException {
        // Given
        WSDL wsdlFirst = getDoclitWsdlContainingGetRandom();
        WSDL wsdlSecond = getDoclitWsdlContainingGetRandom();

        // When/then
        new WSDLMerger(Arrays.asList(wsdlFirst, wsdlSecond), DB_V5_NAME);
    }

    @Test(expected = InvalidWSDLCombinationException.class)
    public void shouldThrowErrorWhenDifferentXrdNamespaces()
            throws InvalidWSDLCombinationException {
        // Given
        WSDL wsdlFirst = getDoclitWsdlContainingGetRandom();
        WSDL wsdlSecond = getWsdlWithDifferentXrdNamespace();

        // When/then
        new WSDLMerger(Arrays.asList(wsdlFirst, wsdlSecond), DB_V5_NAME);
    }

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

    @Test(expected = InvalidWSDLCombinationException.class)
    public void shouldThrowErrorWhenDifferentWsdlStyles()
            throws InvalidWSDLCombinationException {
        // Given
        WSDL wsdlFirst = getDoclitWsdlContainingSmallAttachment();
        WSDL wsdlSecond = getRpcWsdlContainingGetRandom();

        // When/then
        new WSDLMerger(Arrays.asList(wsdlFirst, wsdlSecond), DB_V5_NAME);
    }

    @Test(expected = InvalidWSDLCombinationException.class)
    public void shouldNotAllowMergingMultipleRpcWsdls()
            throws InvalidWSDLCombinationException {
        // Given
        WSDL wsdlFirst = getRpcWsdlContainingGetRandom();
        WSDL wsdlSecond = getRpcWsdlContainingSmallAttachment();

        // When/then
        new WSDLMerger(Arrays.asList(wsdlFirst, wsdlSecond), DB_V5_NAME);
    }

    // -- Methods for getting WSDL - start ---

    private WSDL getDoclitWsdlContainingGetRandom() {
        return getWsdlContainingGetRandom(true);
    }

    private WSDL getRpcWsdlContainingGetRandom() {
        return getWsdlContainingGetRandom(false);
    }

    private WSDL getRpcWsdlContainingSmallAttachment() {
        return new MockWSDLCreator(
                "smallAttachment", false, "v1", "", TestNS.XROAD_NS)
                .getWSDL();
    }

    private WSDL getDoclitWsdlContainingSmallAttachment() {
        return new MockWSDLCreator(
                "smallAttachment", true, "v1", "", TestNS.XROAD_NS)
                .getWSDL();
    }

    private WSDL getWsdlContainingGetRandomNewerVersion() {
        return new MockWSDLCreator(
                "getRandom", true, "v2", "_2", TestNS.XROAD_NS)
                .getWSDL();
    }

    private WSDL getWsdlContainingGetRandom(boolean isDoclit) {
        return new MockWSDLCreator(
                "getRandom", isDoclit, "v1", "", TestNS.XROAD_NS)
                .getWSDL();
    }

    private WSDL getWsdlWithDifferentXrdNamespace() {
        return new MockWSDLCreator(
                "smallAttachment",
                true,
                "v1",
                "",
                "http://www.xrd-different.xom").getWSDL();
    }

    private WSDL getExpectedSimpleWsdl() {
        return new MockWSDLCreator(
                Arrays.asList("getRandom", "smallAttachment"),
                true,
                "v1",
                "",
                TestNS.XROAD_NS,
                TestNS.XRDDL_NEW_TNS,
                DB_V5_NAME).getWSDL();
    }

    private WSDL getExpectedReducedWsdl() {
        return new MockWSDLCreator(
                Arrays.asList("getRandom"),
                true,
                "v2",
                "_2",
                TestNS.XROAD_NS,
                TestNS.XRDDL_NEW_TNS,
                DB_V5_NAME).getWSDL();
    }

    // -- Methods for getting WSDL - end ---

}
