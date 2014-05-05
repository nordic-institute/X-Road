package ee.cyber.xroad.mediator.service.wsdlmerge.structure;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import javax.xml.namespace.QName;

import org.apache.commons.io.FileUtils;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;

/**
 * Covers classes {@link PortType} and {@link PortOperation}.
 */
public class PortTypeBehavior {
    private static final String XRD_NS = "http://andmed.x-road.ee/producer";

    // TODO
    // @Test
    public void shouldTurnPortTypeIntoXml() throws Exception {
        // Given
        QName input = new QName(XRD_NS, "theRequest");
        QName output = new QName(XRD_NS, "theRequestResponse");

        XrdNode docNode = Mockito.mock(XrdNode.class);
        Mockito.when(docNode.getXml())
                .thenReturn("<xrd:title>Tiitel</xrd:title>");

        List<PortOperation> operations = Arrays.asList(
                new PortOperation(
                        "portOp",
                        input,
                        output,
                        Arrays.asList(docNode)));

        PortType portType = new PortType("testPort", operations);

        // When
        String actualXml = portType.getXml();

        // Then
        String expectedXml = FileUtils.readFileToString(new File(
                "src/test/resources/structure/portType.expected")).trim();

        assertEquals(expectedXml, actualXml);
    }
}
