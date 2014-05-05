package ee.cyber.xroad.mediator.service;

import java.util.List;

import javax.wsdl.*;
import javax.wsdl.extensions.UnknownExtensibilityElement;
import javax.wsdl.factory.WSDLFactory;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static ee.cyber.xroad.mediator.message.XRoadNamespaces.NS_DL_XX;

class DummyWSDLCreator {

    private static final String SOAP12_NAMESPACE =
            "http://schemas.xmlsoap.org/wsdl/soap12/";

    private static final String TARGET_NAMESPACE =
            "http://sdsb-andmekogu.x-road.ee/producer";

    private final String address;

    private Document doc;
    private Definition def;

    DummyWSDLCreator(String address) {
        this.address = address;
    }

    Definition create(List<String> methods) throws Exception {
        createDocument();
        createDefinition();

        int i = 1;
        for (String method : methods) {
            String name = "service" + i++;

            def.addPortType(createPortType(name));
            def.addBinding(createBinding(name, method));

            addService(name, method);
        }

        return def;
    }

    private void createDefinition() throws WSDLException {
        WSDLFactory factory = WSDLFactory.newInstance();

        def = factory.newDefinition();
        def.addNamespace("tns", TARGET_NAMESPACE);
        def.addNamespace("xrd", NS_DL_XX);

        def.setQName(new QName("dummyNS", "dummy"));
        def.setTargetNamespace(TARGET_NAMESPACE);
    }

    private void createDocument() throws ParserConfigurationException {
        DocumentBuilderFactory docFactory =
                DocumentBuilderFactory.newInstance();
        doc = docFactory.newDocumentBuilder().newDocument();
    }

    private void addService(String name, String method) {
        Service service = def.createService();
        service.setQName(new QName(name));
        addPort(service, name, method);

        def.addService(service);
    }

    private void addPort(Service service, String name, String method) {
        Port port = def.createPort();

        Element soapAddress = doc.createElementNS(SOAP12_NAMESPACE, "address");
        soapAddress.setPrefix("soap");
        soapAddress.setAttribute("location", address);
        def.addNamespace("soap", SOAP12_NAMESPACE);

        port.addExtensibilityElement(createExtensibilityElement(soapAddress));

        port.setBinding(createBinding(name, method));
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

        binding.setPortType(createPortType(name));
        binding.setUndefined(false);

        return binding;
    }

    private void addBindingOp(Binding binding, String method) {
        String[] methodParts = method.split("\\.");
        String serviceCode = methodParts[1];
        String serviceVersion = methodParts.length >= 3
                ? methodParts[2]
                : null;

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

        binding.addBindingOperation(bindingOperation);
    }

    private Operation createOperation(String name) {
        Operation op = def.createOperation();
        op.setName(name);
        op.setUndefined(false);

        return op;
    }

    private UnknownExtensibilityElement createExtensibilityElement(
            Element element) {
        UnknownExtensibilityElement ext = new UnknownExtensibilityElement();
        ext.setElementType(new QName("dummyNS", "dummy"));
        ext.setRequired(Boolean.FALSE);
        ext.setElement(element);

        return ext;
    }

    private PortType createPortType(String name) {
        PortType portType = def.createPortType();
        portType.setQName(new QName(name + "Name"));
        portType.addOperation(createOperation(name));
        portType.setUndefined(false);

        return portType;
    }

}
