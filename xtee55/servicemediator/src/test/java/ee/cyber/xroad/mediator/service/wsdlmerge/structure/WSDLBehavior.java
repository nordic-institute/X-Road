package ee.cyber.xroad.mediator.service.wsdlmerge.structure;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.mockito.Mockito;

import ee.cyber.xroad.mediator.service.wsdlmerge.TestNS;
import ee.cyber.xroad.mediator.service.wsdlmerge.structure.binding.Binding;

import static org.junit.Assert.assertEquals;

/**
 * Tests behavior of WSDL domain object.
 */
public class WSDLBehavior {

    /**
     * Tests that WSDL can be turned into XML.
     *
     * @throws Exception thrown when turning WSDL into XML fails.
     */
    @Test
    public void shouldReturnWsdlIntoXml() throws Exception {
        // Given

        WSDL wsdl = new WSDL(
                getSchemaElements(),
                getMessages(),
                getPortTypes(),
                getBindings(),
                getServices(),
                TestNS.XROAD_NS,
                TestNS.XRDDL_TNS,
                "mergedWSDL");

        // When
        String actualXml = wsdl.getXml();

        // Then
        String expectedXml = FileUtils.readFileToString(new File(
                "src/test/resources/structure/wsdl.expected")).trim();

        assertEquals(expectedXml, actualXml);
    }

    private List<XrdNode> getSchemaElements() throws Exception {
        XrdNode firstElement = Mockito.mock(XrdNode.class);
        Mockito.when(firstElement.getXml())
                .thenReturn("<FirstMockedSchemaElement />");

        XrdNode secondElement = Mockito.mock(XrdNode.class);
        Mockito.when(secondElement.getXml())
                .thenReturn("<SecondMockedSchemaElement />");

        return Arrays.asList(firstElement, secondElement);
    }

    private List<Message> getMessages() throws IOException {
        Message firstMessage = Mockito.mock(Message.class);
        Mockito.when(firstMessage.getXml())
                .thenReturn("<FirstMockedMessage />");

        Message secondMessage = Mockito.mock(Message.class);
        Mockito.when(secondMessage.getXml())
                .thenReturn("<SecondMockedMessage />");

        return Arrays.asList(firstMessage, secondMessage);
    }

    private List<PortType> getPortTypes() throws Exception {
        PortType portType = Mockito.mock(PortType.class);
        Mockito.when(portType.getXml())
                .thenReturn("<MockedPortType />");

        return Arrays.asList(portType);
    }

    private List<Binding> getBindings() throws Exception {
        Binding binding = Mockito.mock(Binding.class);
        Mockito.when(binding.getXml())
                .thenReturn("<MockedBinding />");

        return Arrays.asList(binding);
    }

    private List<Service> getServices() throws Exception {
        Service service = Mockito.mock(Service.class);
        Mockito.when(service.getXml())
                .thenReturn("<MockedService />");

        return Arrays.asList(service);
    }
}
