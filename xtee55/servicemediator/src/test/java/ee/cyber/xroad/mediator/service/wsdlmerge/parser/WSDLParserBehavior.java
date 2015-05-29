package ee.cyber.xroad.mediator.service.wsdlmerge.parser;

import javax.xml.namespace.QName;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;

import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.ExpectedCodedException;
import ee.cyber.xroad.mediator.service.wsdlmerge.structure.*;
import ee.cyber.xroad.mediator.service.wsdlmerge.structure.binding.Binding;
import ee.cyber.xroad.mediator.service.wsdlmerge.structure.binding.BindingOperation;

import static org.junit.Assert.*;

/**
 * Tests cover WSDL parsing logic, including classes
 * {@link WSDLParser}, {@link ExtensibilityElementParser},
 * {@link ExtensibilityElementAttributeParser} and
 * {@link ExtensibilityElementTextParser}.
 */
public class WSDLParserBehavior {

    private static final String DOCLIT_TNS_URI =
            "http://xrddl-andmekogu.x-road.ee/producer";

    @Rule
    public ExpectedCodedException thrown =
            ExpectedCodedException.none();

    /**
     * Tests that document/literal WSDL can be parsed properly.
     *
     * @throws Exception thrown when document/literal WSDL cannot be parsed.
     */
    @Test
    public void shouldParseDoclitWSDL() throws Exception {
        // Given
        InputStream wsdlInputStream = new FileInputStream(
                "src/test/resources/xrddl.wsdl");

        // When
        WSDLParser parser = new WSDLParser(wsdlInputStream, 1);

        // Then
        WSDL result = parser.getWSDL();

        assertDoclitSchemaElements(result.getSchemaElements());
        assertDoclitMessages(result.getMessages());
        assertDoclitPortTypes(result.getPortTypes());
        assertDoclitBindings(result.getBindings());
        assertDoclitService(result.getServices());

        assertEquals(
                "http://x-road.eu/xsd/x-road.xsd", result.getXrdNamespace());
        assertEquals(
                "http://xrddl-andmekogu.x-road.ee/producer",
                result.getTargetNamespace());
    }

    /**
     * Tests that parsing RPC WSDL is not allowed.
     *
     * @throws Exception indicates success if CodedException with error code
     * X_IO_ERROR is thrown.
     */
    @Test
    public void shouldThrowErrorWhenTryingToParseRpcWsdl() throws Exception {
        thrown.expectError(ErrorCodes.X_IO_ERROR);

        // Given
        InputStream wsdlInputStream = new FileInputStream(
                "src/test/resources/xrdrpc.wsdl");

        // When/then
        new WSDLParser(wsdlInputStream, 1);
    }

    // -- Doclit assertions - start ---

    private void assertDoclitSchemaElements(List<XrdNode> schemaElements) {
        assertEquals(2, schemaElements.size());

        XrdNode requestElement = schemaElements.get(0);
        assertEquals("xrddlGetRandom", requestElement.getName());

        XrdNode responseElement = schemaElements.get(1);
        assertEquals("xrddlGetRandomResponse", responseElement.getName());
    }

    private void assertDoclitMessages(List<Message> messages) {
        String headerNamespaceUri = "http://x-road.eu/xsd/x-road.xsd";

        // Standard header
        List<MessagePart> standardHeaderParts = Arrays
                .asList(
                        new MessagePart("consumer", new QName(
                                headerNamespaceUri, "consumer"), null),
                        new MessagePart("producer", new QName(
                                headerNamespaceUri, "producer"), null),
                        new MessagePart("userId", new QName(headerNamespaceUri,
                                "userId"), null),
                        new MessagePart("service", new QName(
                                headerNamespaceUri, "service"), null),
                        new MessagePart("id", new QName(headerNamespaceUri,
                                "id"), null)
                );
        // Request
        List<MessagePart> requestParts = Arrays.asList(
                new MessagePart("body",
                        new QName(DOCLIT_TNS_URI, "xrddlGetRandom_1"), null)
                );

        // Response
        List<MessagePart> responseParts = Arrays.asList(
                new MessagePart("body",
                        new QName(DOCLIT_TNS_URI, "xrddlGetRandomResponse_1"),
                        null)
                );

        List<String> expectedMessageNames = Arrays.asList(
                "standardheader",
                "xrddlGetRandom_1",
                "xrddlGetRandomResponse_1");

        // We use containsAll when asserting as order is not guaranteed.
        for (Message each : messages) {
            String messageName = each.getName();
            assertTrue(expectedMessageNames.contains(messageName));

            if ("standardheader".equals(messageName)) {
                assertTrue(each.getParts().containsAll(standardHeaderParts));
                assertTrue(each.isXrdStandardHeader());
            } else if ("xrddlGetRandom".equals(messageName)) {
                assertTrue(each.getParts().containsAll(requestParts));
                assertFalse(each.isXrdStandardHeader());
            } else if ("xrddlGetRandomResponse".equals(messageName)) {
                assertTrue(each.getParts().containsAll(responseParts));
                assertFalse(each.isXrdStandardHeader());
            }
        }
    }

    private void assertDoclitPortTypes(List<PortType> portTypes)
            throws Exception {
        QName expectedInput = new QName(DOCLIT_TNS_URI, "xrddlGetRandom_1");
        QName expectedOutput = new QName(DOCLIT_TNS_URI,
                "xrddlGetRandomResponse_1");

        assertEquals(1, portTypes.size());

        PortType portType = portTypes.get(0);
        assertEquals("xrddlGetRandom", portType.getName());

        List<PortOperation> operations = portType.getOperations();
        assertEquals(1, operations.size());
        PortOperation firstOp = operations.get(0);

        assertEquals(expectedInput, firstOp.getInput());
        assertEquals(expectedOutput, firstOp.getOutput());
    }

    private void assertDoclitBindings(List<Binding> bindings) throws Exception {
        QName expectedBindingType = new QName(DOCLIT_TNS_URI,
                "xrddlGetRandom");

        assertEquals(1, bindings.size());

        Binding actualBinding = bindings.get(0);
        assertEquals("xrddlGetRandomSOAP", actualBinding.getName());
        assertEquals(expectedBindingType, actualBinding.getType());

        List<BindingOperation> actualBindingOps = actualBinding.getOperations();
        assertEquals(1, actualBindingOps.size());

        BindingOperation actualOperation = actualBindingOps.get(0);
        assertEquals("xrddlGetRandom", actualOperation.getName());
        assertEquals("v1", actualOperation.getVersion());
    }

    private void assertDoclitService(List<Service> services) throws Exception {
        QName expectedBinding = new QName(DOCLIT_TNS_URI,
                "xrddlGetRandomSOAP");

        assertEquals(1, services.size());
        Service actualService = services.get(0);

        assertEquals("xrddl-andmekoguService", actualService.getName());

        List<ServicePort> actualServicePorts = actualService.getPorts();
        assertEquals(1, actualServicePorts.size());

        ServicePort actualServicePort = actualServicePorts.get(0);
        assertEquals(expectedBinding, actualServicePort.getBinding());
        assertEquals("xrddlGetRandomSOAP", actualServicePort.getName());
    }

    // -- Doclit assertions - end ---
}
