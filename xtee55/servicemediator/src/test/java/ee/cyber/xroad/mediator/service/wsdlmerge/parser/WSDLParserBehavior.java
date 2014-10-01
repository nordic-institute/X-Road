package ee.cyber.xroad.mediator.service.wsdlmerge.parser;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import javax.xml.namespace.QName;

import org.junit.Test;
import org.w3c.dom.Node;

import ee.cyber.xroad.mediator.service.wsdlmerge.structure.*;
import ee.cyber.xroad.mediator.service.wsdlmerge.structure.binding.Binding;
import ee.cyber.xroad.mediator.service.wsdlmerge.structure.binding.BindingOperation;

import static org.junit.Assert.*;

/**
 * TODO: Will be removed!
 * 
 * Tests cover WSDL parsing logic, including classes 
 * {@link WSDLParser}, {@link ExtensibilityElementParser}, 
 * {@link ExtensibilityElementAttributeParser} and 
 * {@link ExtensibilityElementTextParser}.
 */
public class WSDLParserBehavior {

    private static final String DOCLIT_TNS_URI =
            "http://xrddl-andmekogu.x-road.ee/producer";

    private static final String RPC_TNS_URI =
            "http://producers.xrdrpc-andmekogu.xtee.riik.ee/producer/xrdrpc-andmekogu";

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

        assertTrue(result.isDoclit());
        assertEquals(
                "http://x-road.eu/xsd/x-road.xsd", result.getXrdNamespace());
        assertEquals(
                "http://xrddl-andmekogu.x-road.ee/producer",
                result.getTargetNamespace());
    }

    @Test
    public void shouldParseRpcWSDL() throws Exception {
        // Given
        InputStream wsdlInputStream = new FileInputStream(
                "src/test/resources/xrdrpc.wsdl");

        // When
        WSDLParser parser = new WSDLParser(wsdlInputStream, 1);

        // Then
        WSDL result = parser.getWSDL();

        assertRpcSchemaElements(result.getSchemaElements());
        assertRpcMessages(result.getMessages());
        assertRpcPortTypes(result.getPortTypes());
        assertRpcBindings(result.getBindings());
        assertRpcService(result.getServices());
    }

    @Test
    public void shouldParseDoclitWSDLWithImportedSchema() throws Exception {
        // Given
        InputStream wsdlInputStream = new FileInputStream(
                "src/test/resources/xrddl_IMPORTED.wsdl");

        // When
        WSDLParser parser = new WSDLParser(wsdlInputStream, 1);

        // Then
        WSDL result = parser.getWSDL();

        assertDoclitSchemaElements(result.getSchemaElements());
    }

    // Case from real world - just to see if parser can handle it.
    @Test
    public void shouldParseWSDLOfRahvastikuregister() throws Exception {
        InputStream wsdlInputStream = new FileInputStream(
                "src/test/resources/rr.wsdl");

        // When/then
        new WSDLParser(wsdlInputStream, 1);
    }

    // -- Doclit assertions - start ---

    private void assertDoclitSchemaElements(List<XrdNode> schemaElements) {
        assertEquals(2, schemaElements.size());

        XrdNode requestElement = (XrdNode) schemaElements.get(0);
        assertEquals("xrddlGetRandom", requestElement.getName());

        XrdNode responseElement = (XrdNode) schemaElements.get(1);
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

    // -- RPC assertions - start ---

    private void assertRpcSchemaElements(List<XrdNode> schemaElements) {
        assertEquals(2, schemaElements.size());

        XrdNode requestElement = (XrdNode) schemaElements.get(0);
        Node requestName =
                requestElement.getNode().getAttributes().getNamedItem("name");
        assertEquals("xrdrpcGetRandom_paring", requestName.getTextContent());

        XrdNode responseElement = (XrdNode) schemaElements.get(1);
        Node responseName =
                responseElement.getNode().getAttributes().getNamedItem("name");
        assertEquals("xrdrpcGetRandom_vastus", responseName.getTextContent());
    }

    private void assertRpcMessages(List<Message> messages) {
        String headerNamespaceUri = "http://x-tee.riik.ee/xsd/xtee.xsd";

        // Standard header
        List<MessagePart> standardHeaderParts = Arrays.asList(
                new MessagePart("asutus",
                        new QName(headerNamespaceUri, "asutus"), null),
                new MessagePart("andmekogu",
                        new QName(headerNamespaceUri, "andmekogu"), null),
                new MessagePart("isikukood",
                        new QName(headerNamespaceUri, "isikukood"), null),
                new MessagePart("id",
                        new QName(headerNamespaceUri, "id"), null),
                new MessagePart("nimi",
                        new QName(headerNamespaceUri, "nimi"), null),
                new MessagePart("toimik",
                        new QName(headerNamespaceUri, "toimik"), null)
                );

        // Request
        List<MessagePart> requestParts = Arrays.asList(
                new MessagePart("keha", null,
                        new QName(RPC_TNS_URI, "xrdrpcGetRandom_paring_1"))
                );

        // Response
        List<MessagePart> responseParts = Arrays.asList(
                new MessagePart("paring", null,
                        new QName(RPC_TNS_URI, "xrdrpcGetRandom_paring_1")),
                new MessagePart("keha", null,
                        new QName(RPC_TNS_URI, "xrdrpcGetRandom_vastus_1"))
                );

        List<String> expectedMessageNames = Arrays.asList(
                "standardpais",
                "xrdrpcGetRandom_1",
                "xrdrpcGetRandomResponse_1");

        // We use containsAll when asserting as order is not guaranteed.
        for (Message each : messages) {
            String messageName = each.getName();
            assertTrue(expectedMessageNames.contains(messageName));

            if ("standardpais".equals(messageName)) {
                assertTrue(each.getParts().containsAll(standardHeaderParts));
                assertTrue(each.isXrdStandardHeader());
            } else if ("xrdrpcGetRandom".equals(messageName)) {
                assertTrue(each.getParts().containsAll(requestParts));
                assertFalse(each.isXrdStandardHeader());
            } else if ("xrdrpcGetRandomResponse".equals(messageName)) {
                assertTrue(each.getParts().containsAll(responseParts));
                assertFalse(each.isXrdStandardHeader());
            }
        }
    }

    private void assertRpcPortTypes(List<PortType> portTypes) {
        QName expectedInput = new QName(RPC_TNS_URI, "xrdrpcGetRandom_1");
        QName expectedOutput = new QName(RPC_TNS_URI,
                "xrdrpcGetRandomResponse_1");

        assertEquals(1, portTypes.size());

        PortType portType = portTypes.get(0);
        assertEquals("xrdrpcGetRandom", portType.getName());

        List<PortOperation> operations = portType.getOperations();
        assertEquals(1, operations.size());
        PortOperation firstOp = operations.get(0);

        assertEquals(expectedInput, firstOp.getInput());
        assertEquals(expectedOutput, firstOp.getOutput());
    }

    private void assertRpcBindings(List<Binding> bindings) {
        QName expectedBindingType = new QName(RPC_TNS_URI, "xrdrpcGetRandom");

        assertEquals(1, bindings.size());

        Binding actualBinding = bindings.get(0);
        assertEquals("xrdrpcGetRandomSOAP", actualBinding.getName());
        assertEquals(expectedBindingType, actualBinding.getType());

        List<BindingOperation> actualBindingOps = actualBinding.getOperations();
        assertEquals(1, actualBindingOps.size());

        BindingOperation actualOperation = actualBindingOps.get(0);
        assertEquals("xrdrpcGetRandom", actualOperation.getName());
        assertEquals("v1", actualOperation.getVersion());
    }

    private void assertRpcService(List<Service> services) {
        QName expectedBinding = new QName(RPC_TNS_URI, "xrdrpcGetRandomSOAP");

        assertEquals(1, services.size());
        Service actualService = services.get(0);

        assertEquals("xrdrpc-andmekoguService", actualService.getName());

        List<ServicePort> actualServicePorts = actualService.getPorts();
        assertEquals(1, actualServicePorts.size());

        ServicePort actualServicePort = actualServicePorts.get(0);
        assertEquals(expectedBinding, actualServicePort.getBinding());
        assertEquals("xrdrpcGetRandomSOAP", actualServicePort.getName());
    }

    // -- RPC assertions - end ---
}
