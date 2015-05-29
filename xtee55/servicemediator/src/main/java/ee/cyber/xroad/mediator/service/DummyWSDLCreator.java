package ee.cyber.xroad.mediator.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.wsdl.*;
import javax.wsdl.extensions.ExtensibilityElement;
import javax.wsdl.extensions.UnknownExtensibilityElement;
import javax.wsdl.extensions.schema.Schema;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLReader;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import ee.ria.xroad.common.util.ResourceUtils;

import static ee.cyber.xroad.mediator.message.V5XRoadNamespaces.NS_DL_XX;

class DummyWSDLCreator {

    private static final String SOAP12_NAMESPACE =
            "http://schemas.xmlsoap.org/wsdl/soap12/";

    private static final String TARGET_NAMESPACE =
            "http://xroad-andmekogu.x-road.ee/producer";
    public static final String XROAD_SERVICE_BINDING_NAME = "xroadServiceBinding";

    private final String address;

    private Document doc;
    private Definition def;

    private final Map<String, String> namesToMethods = new LinkedHashMap<>();

    DummyWSDLCreator(String address) {
        this.address = address;
    }

    Definition create(List<String> methods) throws Exception {
        createDocument();
        createDefinition();
        mapNamesWithMethods(methods);

        addSchemaElements();
        addMessages();
        addPortOperations();
        addBindingOperations();
        updateService();

        return def;
    }

    private void createDefinition() throws WSDLException, IOException {
        WSDLReader wsdlReader = WSDLFactory.newInstance().newWSDLReader();
        wsdlReader.setFeature("javax.wsdl.importDocuments", false);
        wsdlReader.setFeature("com.ibm.wsdl.parseXMLSchemas", true);

        try (InputStream wsdlStream =
                     ResourceUtils.getClasspathResourceStream("dummy.wsdl")) {
            def = wsdlReader.readWSDL(null, new InputSource(wsdlStream));

            def.setQName(new QName("dummyNS", "dummy"));
            def.setTargetNamespace(TARGET_NAMESPACE);
        }
    }

    private void createDocument() throws ParserConfigurationException {
        DocumentBuilderFactory docFactory =
                DocumentBuilderFactory.newInstance();
        doc = docFactory.newDocumentBuilder().newDocument();
    }

    private void mapNamesWithMethods(List<String> methods) {
        int i = 1;

        for (String eachMethod : methods) {
            String name = "service" + i++;

            namesToMethods.put(name, eachMethod);
        }
    }

    private void addSchemaElements() {
        for (Object elem : def.getTypes().getExtensibilityElements()) {
            if (elem instanceof Schema) {
                Schema schema = (Schema) elem;
                processNames(name -> {
                    addElementToSchema(getRequestElementName(name), schema);
                    addElementToSchema(getResponseElementName(name), schema);
                });
                break;
            }
        }
    }

    private void addElementToSchema(String name, Schema schema) {
        final Element schemaRoot = schema.getElement();
        Element newElement =
                schemaRoot.getOwnerDocument().createElement("xsd:element");

        newElement.setAttribute("name", name);
        newElement.setAttribute("type", "xsd:string");

        schemaRoot.appendChild(newElement);
    }

    private void addMessages() {
        processNames(name -> {
            addOperationMessages(getRequestElementName(name));
            addOperationMessages(getResponseElementName(name));
        });
    }

    private void addOperationMessages(String name) {
        Message message = def.createMessage();
        message.setQName(new QName(TARGET_NAMESPACE, name));

        Part part = def.createPart();
        part.setName("body");
        part.setElementName(new QName(TARGET_NAMESPACE, name));

        message.addPart(part);

        message.setUndefined(false);
        def.addMessage(message);
    }

    private void addPortOperations() {
        PortType portType = def.getPortType(
                new QName(TARGET_NAMESPACE, "xroadServicePortType"));

        processNames(name -> addPortOperation(name, portType));
    }

    private void addPortOperation(String name, PortType portType) {
        final Operation operation = def.createOperation();
        operation.setName(getServiceCode(name));

        final Input input = def.createInput();
        input.setMessage(def.getMessage(
                new QName(TARGET_NAMESPACE, getRequestElementName(name))));
        operation.setInput(input);

        final Output output = def.createOutput();
        output.setMessage(def.getMessage(
                new QName(TARGET_NAMESPACE, getResponseElementName(name))));
        operation.setOutput(output);

        operation.setUndefined(false);
        portType.setUndefined(false);
        portType.addOperation(operation);
    }

    private void addBindingOperations() {
        Binding binding = def.getBinding(
                new QName(TARGET_NAMESPACE, XROAD_SERVICE_BINDING_NAME));
        processNames(name -> addBindingOp(binding, namesToMethods.get(name)));
    }

    private void updateService() {
        Service service = def.getService(
                new QName(TARGET_NAMESPACE, "xroad-andmekoguService"));
        addPort(service, "xroadServicePort", "dummy");
    }

    private void addPort(Service service, String name, String method) {
        Port port = def.createPort();

        Element soapAddress = doc.createElementNS(SOAP12_NAMESPACE, "address");
        soapAddress.setPrefix("soap");
        soapAddress.setAttribute("location", address);
        def.addNamespace("soap", SOAP12_NAMESPACE);

        port.addExtensibilityElement(createExtensibilityElement(soapAddress));

        port.setBinding(createBinding(XROAD_SERVICE_BINDING_NAME, method));
        port.setName(name);

        service.addPort(port);
    }

    private Binding createBinding(String name, String method) {
        Binding binding = def.createBinding();
        binding.setQName(new QName(TARGET_NAMESPACE, name, "tns"));

        Element soapBinding = doc.createElementNS(SOAP12_NAMESPACE, "binding");
        soapBinding.setPrefix("soap");
        soapBinding.setAttribute("style", "document");
        soapBinding.setAttribute("transport",
                "http://schemas.xmlsoap.org/soap/http");

        binding.addExtensibilityElement(
                createExtensibilityElement(soapBinding));

        addBindingOp(binding, method);

        binding.setPortType(
                def.getPortType(new QName(TARGET_NAMESPACE, name)));
        binding.setUndefined(false);

        return binding;
    }

    private void addBindingOp(Binding binding, String method) {
        String[] methodParts = method.split("\\.");

        boolean hasVersionPart =
            methodParts[methodParts.length - 1].matches("^v[\\d]+$");

        String serviceVersion = hasVersionPart
            ? methodParts[methodParts.length - 1]
            : null;

        String serviceCode = hasVersionPart
            ? methodParts[methodParts.length - 2]
            : methodParts[methodParts.length - 1];

        BindingOperation bindingOperation = def.createBindingOperation();
        bindingOperation.setName(serviceCode);

        Element soapOperation =
                doc.createElementNS(SOAP12_NAMESPACE, "operation");
        soapOperation.setPrefix("soap");
        soapOperation.setAttribute("soapAction", "");
        soapOperation.setAttribute("style", "document");

        bindingOperation.addExtensibilityElement(
                createExtensibilityElement(soapOperation));

        if (serviceVersion != null) {
            Element xrdVersion = doc.createElementNS(NS_DL_XX, "version");
            xrdVersion.setPrefix("xrd");
            xrdVersion.setTextContent(serviceVersion);

            bindingOperation.addExtensibilityElement(
                    createExtensibilityElement(xrdVersion));
        }

        BindingInput bindingInput = def.createBindingInput();
        bindingInput.addExtensibilityElement(createSoapBodyElement());
        bindingOperation.setBindingInput(bindingInput);

        BindingOutput bindingOutput = def.createBindingOutput();
        bindingOutput.addExtensibilityElement(createSoapBodyElement());
        bindingOperation.setBindingOutput(bindingOutput);

        binding.addBindingOperation(bindingOperation);
    }

    private ExtensibilityElement createSoapBodyElement() {
        Element soapBodyElement =
                doc.createElementNS(SOAP12_NAMESPACE, "body");
        soapBodyElement.setPrefix("soap");
        soapBodyElement.setAttribute("parts", "body");
        soapBodyElement.setAttribute("use", "literal");

        return createExtensibilityElement(soapBodyElement);
    }

    private UnknownExtensibilityElement createExtensibilityElement(
            Element element) {
        UnknownExtensibilityElement ext = new UnknownExtensibilityElement();
        ext.setElementType(new QName("dummyNS", "dummy"));
        ext.setRequired(Boolean.FALSE);
        ext.setElement(element);

        return ext;
    }

    private String getRequestElementName(String name) {
        return name + "Request";
    }

    private String getResponseElementName(String name) {
        return name + "Response";
    }

    private void processNames(Consumer<String> action) {
        namesToMethods.keySet().forEach(action);
    }

    private String getServiceCode(String name) {
        String rawCode = namesToMethods.get(name);
        return rawCode.split("\\.")[1];
    }
}
