package ee.cyber.xroad.mediator.service.wsdlmerge.parser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.wsdl.Definition;
import javax.wsdl.Input;
import javax.wsdl.Output;
import javax.wsdl.Part;
import javax.wsdl.Port;
import javax.wsdl.WSDLException;
import javax.wsdl.extensions.UnknownExtensibilityElement;
import javax.wsdl.extensions.schema.Schema;
import javax.wsdl.extensions.schema.SchemaImport;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLReader;
import javax.xml.namespace.QName;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import ee.cyber.sdsb.common.message.SoapHeader;
import ee.cyber.xroad.mediator.message.XRoadNamespaces;
import ee.cyber.xroad.mediator.service.wsdlmerge.structure.*;
import ee.cyber.xroad.mediator.service.wsdlmerge.structure.binding.Binding;
import ee.cyber.xroad.mediator.service.wsdlmerge.structure.binding.BindingOperation;
import ee.cyber.xroad.mediator.service.wsdlmerge.structure.binding.DoclitBinding;
import ee.cyber.xroad.mediator.service.wsdlmerge.structure.binding.DoclitBindingOperation;
import ee.cyber.xroad.mediator.service.wsdlmerge.structure.binding.RpcBinding;
import ee.cyber.xroad.mediator.service.wsdlmerge.structure.binding.RpcBindingOperation;

/**
 * Parses WSDL to get data sufficient to merge WSDL-s later.
 */
public class WSDLParser {
    private static final Logger LOG = LoggerFactory.getLogger(WSDLParser.class);

    private static final String SOAP_NS =
            "http://schemas.xmlsoap.org/wsdl/soap/";
    private static final String SOAP12_NS =
            "http://schemas.xmlsoap.org/wsdl/soap12/";
    private static final String XSD_NS =
            "http://www.w3.org/2001/XMLSchema";

    private static final List<String> SUPPORTED_XRD_NAMESPACES;

    private static final NamespaceMatchCondition SOAP_NS_MATCH_CONDITION;
    private static final NamespaceMatchCondition XRD_NS_MATCH_CONDITION;

    static {
        SUPPORTED_XRD_NAMESPACES = Arrays.asList(
                XRoadNamespaces.NS_RPC,
                XRoadNamespaces.NS_DL_EE,
                XRoadNamespaces.NS_DL_EU,
                XRoadNamespaces.NS_DL_XX,
                SoapHeader.NS_SDSB
        );

        SOAP_NS_MATCH_CONDITION =
                new NamespaceMatchCondition() {
            @Override
            public boolean matches(String value) {
                return SOAP_NS.equals(value) || SOAP12_NS.equals(value);
            }

            @Override
            public String getErrorMsg() {
                return String.format(
                        "No valid SOAP namespace found, legal values are '%s' " +
                                "and '%s', check correctness of Your WSDL.",
                        SOAP_NS, SOAP12_NS);
            }
        };

        XRD_NS_MATCH_CONDITION =
                new NamespaceMatchCondition() {
            @Override
            public boolean matches(String value) {
                return SUPPORTED_XRD_NAMESPACES.contains(value);
            }

            @Override
            public String getErrorMsg() {
                return String.format(
                        "Could not find valid X-Road namespace, valid ones are [%s]",
                        StringUtils.join(SUPPORTED_XRD_NAMESPACES, ", "));
            }
        };
    }

    /** Ensures uniqueness of referenced WSDL elements. */
    private int orderNo;

    private List<XrdNode> schemaElements = new ArrayList<>();
    private List<Message> messages = new ArrayList<>();
    private List<PortType> portTypes = new ArrayList<>();
    private List<Binding> bindings = new ArrayList<>();
    private List<Service> services = new ArrayList<>();

    private WSDLStyle wsdlStyle;
    private String xrdNamespace;
    private String soapNamespace;
    private String targetNamespace;

    public WSDLParser(InputStream wsdlInputStream, int orderNo)
            throws Exception {
        this.orderNo = orderNo;

        parse(wsdlInputStream);
    }

    private void parse(InputStream wsdlInputStream) throws Exception {
        Definition definition = getWSDLDefinition(wsdlInputStream);
        setNamespaces(definition);
        setWSDLStyle(definition);

        parseSchema(definition);
        parseMessages(definition);
        parsePortTypes(definition);
        parseBindings(definition);
        parseService(definition);
    }

    private void setNamespaces(Definition definition) throws WSDLException {
        this.soapNamespace = parseNamespaceFromHeader(
                definition, SOAP_NS_MATCH_CONDITION);

        this.xrdNamespace = parseNamespaceFromHeader(
                definition, XRD_NS_MATCH_CONDITION);

        this.targetNamespace = definition.getTargetNamespace();

        LOG.trace("SOAP namespace: '{}'", this.soapNamespace);
        LOG.trace("X-Road namespace: '{}'", this.xrdNamespace);
        LOG.trace("Target namespace: '{}'", this.targetNamespace);
    }

    @SuppressWarnings("unchecked")
    private String parseNamespaceFromHeader(Definition definition,
            NamespaceMatchCondition condition) throws WSDLException {
        for (Object each : definition.getNamespaces().entrySet()) {
            Map.Entry<String, String> entry = (Map.Entry<String, String>) each;
            String namespace = entry.getValue();

            if (condition.matches(namespace)) {
                return namespace;
            }
        }

        throw new WSDLException(
                WSDLException.INVALID_WSDL, condition.getErrorMsg());
    }

    private void setWSDLStyle(Definition definition) {
        Object[] bindingsArray = definition.getBindings().values().toArray();

        if (bindingsArray.length == 0) {
            this.wsdlStyle = new DoclitWSDLStyle();
        }

        javax.wsdl.Binding firstBinding =
                (javax.wsdl.Binding) bindingsArray[0];

        QName soapBinding = new QName(soapNamespace, "binding");
        String soapBindingStyle = new ExtensibilityElementAttributeParser(
                firstBinding.getExtensibilityElements(),
                soapBinding).parse();

        this.wsdlStyle = "document".equals(soapBindingStyle)
                ? new DoclitWSDLStyle() : new RpcWSDLStyle();

        LOG.trace("WSDL style set to '{}'",
                this.wsdlStyle.getClass().getSimpleName());
    }

    private Definition getWSDLDefinition(InputStream wsdlInputStream)
            throws WSDLException {
        InputSource inputSource = new InputSource(wsdlInputStream);
        WSDLReader wsdlReader = WSDLFactory.newInstance().newWSDLReader();
        wsdlReader.setFeature("javax.wsdl.importDocuments", true);

        Definition definition = wsdlReader.readWSDL(null, inputSource);
        return definition;
    }

    // -- Parsing WSDL parts - start ---

    private void parseSchema(Definition definition) throws Exception {
        for (Object each : definition.getTypes().getExtensibilityElements()) {
            if (each instanceof Schema) {
                Schema schema = (Schema) each;
                Element element = schema.getElement();

                parseSchemaElements(element.getChildNodes());
                parseImportedSchemas(schema);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void parseImportedSchemas(Schema schema) throws Exception {
        for (Object eachImport : schema.getImports().entrySet()) {
            Map.Entry<String, Vector<SchemaImport>> imports =
                    (Map.Entry<String, Vector<SchemaImport>>) eachImport;
            parseReferencedSchema(imports);
        }
    }

    private void parseReferencedSchema(
            Map.Entry<String, Vector<SchemaImport>> imports) throws Exception {
        String importNamespace = imports.getKey();

        if (!StringUtils.equals(targetNamespace, importNamespace)) {
            return;
        }

        for (SchemaImport each : imports.getValue()) {
            Schema referencedSchema = each.getReferencedSchema();
            parseSchemaElements(referencedSchema.getElement().getChildNodes());
        }
    }

    private void parseSchemaElements(NodeList rawSchemaElements)
            throws Exception {
        for (int i = 0; i < rawSchemaElements.getLength(); i++) {
            parseSchemaElement(rawSchemaElements.item(i));
        }
    }

    private void parseSchemaElement(Node element) throws Exception {
        if (!wsdlStyle.isSchemaElement(element)) {
            return;
        }

        Node nameAttribute = element.getAttributes().getNamedItem("name");
        String elementName = nameAttribute.getTextContent();
        nameAttribute.setTextContent(elementName);

        XrdNode xrdNode = new XrdNode(element);
        LOG.trace("Added schema element: '{}'", xrdNode.getXml());
        schemaElements.add(xrdNode);
    }

    @SuppressWarnings("unchecked")
    private void parseMessages(Definition definition) {
        for (Object eachMessage : definition.getMessages().entrySet()) {
            Map.Entry<QName, javax.wsdl.Message> entry =
                    (Map.Entry<QName, javax.wsdl.Message>) eachMessage;
            javax.wsdl.Message wsdlMessage = entry.getValue();

            String messageName = entry.getKey().getLocalPart();
            List<MessagePart> parts = new ArrayList<>();

            for (Object eachPart : wsdlMessage.getParts().entrySet()) {
                Map.Entry<String, Part> partEntry =
                        (Map.Entry<String, Part>) eachPart;
                String partName = partEntry.getKey();
                Part partElement = partEntry.getValue();

                parts.add(getNewMessagePart(wsdlMessage, partName, partElement));
            }

            Message message = new Message(
                    getNewMessageName(wsdlMessage, messageName),
                    parts,
                    wsdlStyle.isXrdStandardHeader(wsdlMessage));
            LOG.trace("Message parsed: '{}'", message);
            messages.add(message);
        }
    }

    @SuppressWarnings("unchecked")
    private void parsePortTypes(Definition definition) {
        for (Object eachPortType : definition.getPortTypes().entrySet()) {
            Map.Entry<QName, javax.wsdl.PortType> entry =
                    (Map.Entry<QName, javax.wsdl.PortType>) eachPortType;

            String portTypeName = entry.getKey().getLocalPart();
            javax.wsdl.PortType portTypeContent = entry.getValue();
            List<PortOperation> portOperations = new ArrayList<>();

            for (Object eachOp : portTypeContent.getOperations()) {
                javax.wsdl.Operation operation = (javax.wsdl.Operation) eachOp;

                List<Marshallable> doc = parseOperationDocumentation(operation);
                QName inputQName = parseOperationInput(operation);
                QName outputQName = parseOperationOutput(operation);

                portOperations.add(new PortOperation(
                        operation.getName(),
                        addOrderNo(inputQName),
                        addOrderNo(outputQName),
                        doc));
            }

            PortType portType = new PortType(portTypeName, portOperations);
            LOG.trace("Port type added: '{}'", portType);
            portTypes.add(portType);
        }
    }

    private List<Marshallable> parseOperationDocumentation(
            javax.wsdl.Operation operation) {
        List<Marshallable> result = new ArrayList<>();
        Element docElement = operation.getDocumentationElement();

        if (docElement != null) {
            NodeList docItems = docElement.getChildNodes();

            for (int i = 0; i < docItems.getLength(); i++) {
                result.add(new XrdNode(docItems.item(i)));
            }
        }
        return result;
    }

    private QName parseOperationInput(javax.wsdl.Operation operation) {
        QName inputQName = null;
        Input input = operation.getInput();

        if (input != null) {
            javax.wsdl.Message inputMessage = input.getMessage();
            inputQName = inputMessage.getQName();
        }
        return inputQName;
    }

    private QName parseOperationOutput(javax.wsdl.Operation operation) {
        QName outputQName = null;
        Output output = operation.getOutput();

        if (output != null) {
            javax.wsdl.Message outputMessage = output.getMessage();
            outputQName = outputMessage.getQName();
        }
        return outputQName;
    }

    @SuppressWarnings("unchecked")
    private void parseBindings(Definition definition) {
        for (Object eachBinding : definition.getBindings().entrySet()) {
            Map.Entry<QName, javax.wsdl.Binding> entry =
                    (Map.Entry<QName, javax.wsdl.Binding>) eachBinding;
            javax.wsdl.Binding rawBinding = entry.getValue();

            String bindingName = rawBinding.getQName().getLocalPart();
            QName bindingType = rawBinding.getPortType().getQName();
            List<BindingOperation> newOperations =
                    parseBindingOperations(rawBinding);

            Binding binding = wsdlStyle.getBinding(
                    bindingName,
                    bindingType,
                    newOperations);

            LOG.trace("New binding added: '{}'", binding);
            bindings.add(binding);
        }

    }

    private List<BindingOperation> parseBindingOperations(
            javax.wsdl.Binding rawBinding) {
        List<BindingOperation> newOperations = new ArrayList<>();

        for (Object eachOp : rawBinding.getBindingOperations()) {
            javax.wsdl.BindingOperation bindingOp =
                    (javax.wsdl.BindingOperation) eachOp;
            List<?> opExtElements = bindingOp.getExtensibilityElements();
            String version = new ExtensibilityElementTextParser(
                    opExtElements, new QName(xrdNamespace, "version")).parse();

            List<Marshallable> xrdNodes = getXrdNodes(opExtElements);
            newOperations.add(wsdlStyle.getBindingOperations(
                    bindingOp.getName(), version, xrdNodes));
        }

        return newOperations;
    }

    @SuppressWarnings("unchecked")
    private void parseService(Definition definition) {
        for (Object eachService : definition.getServices().entrySet()) {
            Map.Entry<QName, javax.wsdl.Service> serviceEntry =
                    (Map.Entry<QName, javax.wsdl.Service>) eachService;
            QName serviceQName = serviceEntry.getKey();
            javax.wsdl.Service service = serviceEntry.getValue();

            List<ServicePort> newPorts = parseServicePorts(service);
            Service newService = new Service(
                    serviceQName.getLocalPart(),
                    newPorts);
            LOG.trace("New service added: '{}'", service);
            services.add(newService);
        }

    }

    @SuppressWarnings("unchecked")
    private List<ServicePort> parseServicePorts(javax.wsdl.Service service) {
        List<ServicePort> newPorts = new ArrayList<ServicePort>();

        for (Object eachPort : service.getPorts().entrySet()) {
            Map.Entry<String, Port> portEntry =
                    (Map.Entry<String, Port>) eachPort;
            Port port = portEntry.getValue();

            List<?> extElements = port.getExtensibilityElements();

            List<Marshallable> nodes = getXrdNodes(extElements);
            newPorts.add(new ServicePort(
                    port.getBinding().getQName(),
                    port.getName(),
                    nodes));
        }
        return newPorts;
    }

    private List<Marshallable> getXrdNodes(List<?> opExtElements) {
        List<Marshallable> result = new ArrayList<>(opExtElements.size());

        for (Object each : opExtElements) {
            if (!(each instanceof UnknownExtensibilityElement)) {
                continue;
            }

            Element element = ((UnknownExtensibilityElement) each).getElement();
            result.add(new XrdNode(element));
        }
        return result;
    }

    // -- Parsing WSDL parts - start ---

    private MessagePart getNewMessagePart(
            javax.wsdl.Message wsdlMessage,
            String partName,
            Part partElement) {

        QName elementName = partElement.getElementName();
        QName typeName = partElement.getTypeName();

        LOG.trace("getNewMessagePart(element: '{}', type: '{}')",
                elementName, typeName);

        return new MessagePart(partName, elementName, typeName);
    }

    private String getNewMessageName(javax.wsdl.Message wsdlMessage,
            String messageName) {
        return wsdlStyle.isXrdStandardHeader(wsdlMessage)
                ? messageName
                : addOrderNo(messageName);
    }

    private QName addOrderNo(QName value) {
        if (value == null) {
            return null;
        }

        return new QName(
                value.getNamespaceURI(), addOrderNo(value.getLocalPart()));
    }


    private String addOrderNo(String value) {
        return String.format("%s_%d", value, orderNo);
    }

    public WSDL getWSDL() {
        return new WSDL(
                schemaElements,
                messages,
                portTypes,
                bindings,
                services,
                (wsdlStyle instanceof DoclitWSDLStyle),
                xrdNamespace,
                targetNamespace,
                "");
    }

    /**
     * Encapsulates WSDL style specific parsing logic.
     */
    private abstract static class WSDLStyle {
        boolean isSchemaElement(Node element) {
            String elementLocalName = element.getLocalName();
            if (StringUtils.isBlank(elementLocalName)) {
                return false;
            }

            QName elementQName = new QName(
                    element.getNamespaceURI(), elementLocalName);

            for (QName each : getValidSchemaElementQNames()) {
                if (each.equals(elementQName)) {
                    return true;
                }
            }

            NamedNodeMap attributes = element.getAttributes();

            if (attributes == null) {
                return false;
            }

            Node nameNode = attributes.getNamedItem("name");

            if (nameNode == null) {
                return false;
            }

            return (StringUtils.isNotBlank(nameNode.getTextContent()));
        }

        @SuppressWarnings("unchecked")
        boolean isXrdStandardHeader(javax.wsdl.Message wsdlMessage) {
            Map<String, QName> messageParts = new HashMap<>();
            for (Object each : wsdlMessage.getParts().entrySet()) {
                Map.Entry<String, Part> partEntry =
                        (Map.Entry<String, Part>) each;
                String partName = partEntry.getKey();
                Part part = partEntry.getValue();
                messageParts.put(partName, part.getElementName());
            }

            for (Map.Entry<String, QName> entry :
                    getStandardHeaderParts().entrySet()) {
                String standardHeaderPartName = entry.getKey();
                QName standardHeaderPartElement = entry.getValue();

                QName messagePartName = messageParts
                        .get(standardHeaderPartName);

                if (!messagePartExists(standardHeaderPartElement,
                        messagePartName)) {
                    return false;
                }
            }

            return true;
        }

        protected abstract Binding getBinding(
                String name, QName type, List<BindingOperation> operations);

        protected abstract BindingOperation getBindingOperations(
                String name, String version, List<Marshallable> xrdNodes);

        protected abstract QName[] getValidSchemaElementQNames();

        protected abstract Map<String, QName> getStandardHeaderParts();

        private boolean messagePartExists(QName standardHeaderPartElement,
                QName messagePartName) {
            return messagePartName != null
                    && standardHeaderPartElement.equals(messagePartName);
        }
    }

    private class DoclitWSDLStyle extends WSDLStyle {

        @Override
        protected QName[] getValidSchemaElementQNames() {
            return new QName[] { new QName(XSD_NS, "element") };
        }

        @Override
        protected Map<String, QName> getStandardHeaderParts() {
            Map<String, QName> result = new HashMap<>();

            result.put("consumer", new QName(xrdNamespace, "consumer"));
            result.put("producer", new QName(xrdNamespace, "producer"));
            result.put("userId", new QName(xrdNamespace, "userId"));
            result.put("service", new QName(xrdNamespace, "service"));
            result.put("id", new QName(xrdNamespace, "id"));

            return result;
        }

        @Override
        protected Binding getBinding(String name, QName type,
                List<BindingOperation> operations) {
            return new DoclitBinding(name, type, operations);
        }

        @Override
        protected BindingOperation getBindingOperations(String name,
                String version, List<Marshallable> xrdNodes) {
            return new DoclitBindingOperation(name, version, xrdNodes);
        }
    }

    private class RpcWSDLStyle extends WSDLStyle {

        @Override
        protected QName[] getValidSchemaElementQNames() {
            return new QName[] {
                    new QName(XSD_NS, "simpleType"),
                    new QName(XSD_NS, "complexType")
            };
        }

        @Override
        protected Map<String, QName> getStandardHeaderParts() {
            Map<String, QName> result = new HashMap<>();

            result.put("asutus", new QName(xrdNamespace, "asutus"));
            result.put("andmekogu", new QName(xrdNamespace, "andmekogu"));
            result.put("isikukood", new QName(xrdNamespace, "isikukood"));
            result.put("id", new QName(xrdNamespace, "id"));
            result.put("nimi", new QName(xrdNamespace, "nimi"));

            return result;
        }

        @Override
        protected Binding getBinding(String name, QName type,
                List<BindingOperation> operations) {
            return new RpcBinding(name, type, operations);
        }

        @Override
        protected BindingOperation getBindingOperations(String name,
                String version, List<Marshallable> xrdNodes) {
            return new RpcBindingOperation(
                    name, version, xrdNodes, targetNamespace);
        }
    }

    private interface NamespaceMatchCondition {
        boolean matches(String value);

        String getErrorMsg();
    }
}
