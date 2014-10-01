package ee.cyber.xroad.mediator.service.wsdlmerge.merger;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
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
    public void shouldReturnSingleWsdlExactlyAsIs() throws Exception {
        // Given
        String wsdlUrl = "http://www.example.com/wsdl";
        List<String> wsdlUrls = Collections.singletonList(wsdlUrl);

        WSDLProvider wsdlProvider = Mockito.mock(WSDLProvider.class);
        Mockito.when(wsdlProvider.getWsdl(wsdlUrl)).thenReturn(
                new FileInputStream(XRDDL_WSDL_FILE));

        ClientId client = ClientId.create("EE", "foo", "bar");

        // When
        WSDLsMerger merger = new WSDLsMerger(
                wsdlUrls, wsdlProvider, client);

        // Then
        String expectedWsdlContent = FileUtils.readFileToString(
                new File(XRDDL_WSDL_FILE));

        String actualWsdlContent = IOUtils.toString(
                merger.getMergedWsdlAsStream());

        assertEquals(expectedWsdlContent, actualWsdlContent);
    }

    @Test
    public void shouldThrowErrorIfNoMergeableWsdlsFound() throws Exception {
        // Given
        thrown.expectError(X_ADAPTER_WSDL_NOT_FOUND);

        // When/then
        new WSDLsMerger(
                new ArrayList<String>(),
                new WSDLProvider(),
                ClientId.create("EE", "foo", "bar"));
    }
}
