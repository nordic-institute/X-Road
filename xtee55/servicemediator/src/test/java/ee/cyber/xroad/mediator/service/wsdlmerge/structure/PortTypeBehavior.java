package ee.cyber.xroad.mediator.service.wsdlmerge.structure;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import javax.xml.namespace.QName;

import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.mockito.Mockito;

import ee.cyber.xroad.mediator.service.wsdlmerge.TestNS;

import static org.junit.Assert.assertEquals;

/**
 * Covers classes {@link PortType} and {@link PortOperation}.
 */
public class PortTypeBehavior {

    /**
     * Tests that port type can be turned into XML.
     *
     * @throws Exception thrown when turning port type into XML fails.
     */
    @Test
    public void shouldTurnPortTypeIntoXml() throws Exception {
        // Given
        QName input = new QName(TestNS.XRDDL_TNS, "theRequest");
        QName output = new QName(TestNS.XRDDL_TNS, "theRequestResponse");

        Marshallable docNode = Mockito.mock(Marshallable.class);
        Mockito.when(docNode.getXml())
                .thenReturn("<xrd:title>Mock op title</xrd:title>");

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
