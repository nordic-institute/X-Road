package ee.cyber.xroad.mediator.service.wsdlmerge.merger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import ee.cyber.sdsb.common.ExpectedCodedException;
import ee.cyber.sdsb.common.identifier.ClientId;

import static ee.cyber.sdsb.common.ErrorCodes.X_ADAPTER_WSDL_NOT_FOUND;
import static org.junit.Assert.assertEquals;

public class WSDLsMergerBehavior {
    private static final String XRDDL_WSDL_FILE =
            "src/test/resources/xrddl.wsdl";

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    @Test
    public void shouldReturnSingleWsdlExactlyAsIs() throws IOException {
        // Given
        String wsdlUrl = "http://www.example.com/wsdl";
        List<String> wsdlUrls = Collections.singletonList(wsdlUrl);

        WSDLProvider wsdlProvider = Mockito.mock(WSDLProvider.class);
        Mockito.when(wsdlProvider.getWsdl(wsdlUrl)).thenReturn(
                new FileInputStream(XRDDL_WSDL_FILE));

        ClientId client = ClientId.create("EE", "foo", "bar");

        // When
        WSDLsMerger merger = new WSDLsMerger(
                wsdlUrls, wsdlProvider, client, new WSDLStreamsMerger());

        // Then
        String expectedWsdlContent = FileUtils.readFileToString(
                new File(XRDDL_WSDL_FILE));

        String actualWsdlContent = IOUtils.toString(
                merger.getMergedWsdlAsStream());

        assertEquals(expectedWsdlContent, actualWsdlContent);
    }

    @Test
    public void shouldThrowErrorIfNoMergeableWsdlsFound() throws IOException {
        // Given
        thrown.expectError(X_ADAPTER_WSDL_NOT_FOUND);

        // When/then
        new WSDLsMerger(
                new ArrayList<String>(),
                new WSDLProvider(),
                ClientId.create("EE", "foo", "bar"),
                new WSDLStreamsMerger());
    }

    @Test
    public void shouldMergeMultipleWsdls() throws IOException {
        // Given
        String wsdlUrl1 = "http://wsdl1.example.com";
        String wsdlUrl2 = "http://wsdl2.example.com";
        List<String> wsdlUrls = Arrays.asList(wsdlUrl1, wsdlUrl2);

        InputStream wsdl1IS = IOUtils.toInputStream("<wsdl1>");
        InputStream wsdl2IS = IOUtils.toInputStream("<wsdl2>");
        List<InputStream> wsdlStreams = Arrays.asList(wsdl1IS, wsdl2IS);

        WSDLProvider wsdlProvider = Mockito.mock(WSDLProvider.class);
        Mockito.when(wsdlProvider.getWsdl(wsdlUrl1)).thenReturn(wsdl1IS);
        Mockito.when(wsdlProvider.getWsdl(wsdlUrl2)).thenReturn(wsdl2IS);

        ClientId client = ClientId.create("EE", "foo", "bar");

        String expectedMergedWsdlContent = "<wsdlMerged>";

        WSDLStreamsMerger streamsMerger = Mockito.mock(WSDLStreamsMerger.class);
        Mockito.when(streamsMerger.merge(wsdlStreams))
                .thenReturn(IOUtils.toInputStream(expectedMergedWsdlContent));

        // When
        WSDLsMerger merger = new WSDLsMerger(
                wsdlUrls, wsdlProvider, client, streamsMerger);

        // Then
        String actualWsdlContent = IOUtils.toString(
                merger.getMergedWsdlAsStream());

        assertEquals(expectedMergedWsdlContent, actualWsdlContent);
    }
}
