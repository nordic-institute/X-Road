package ee.cyber.xroad.mediator.service;

import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.wsdl.Definition;
import javax.wsdl.Operation;
import javax.wsdl.Port;
import javax.wsdl.Service;
import javax.wsdl.extensions.ExtensibilityElement;
import javax.wsdl.extensions.soap.SOAPAddress;
import javax.wsdl.extensions.soap12.SOAP12Address;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLReader;
import javax.xml.namespace.QName;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

final class WSDLParser {

    private static final String SOAP_NAMESPACE =
        "http://schemas.xmlsoap.org/wsdl/soap/";

    private static final String SOAP12_NAMESPACE =
        "http://schemas.xmlsoap.org/wsdl/soap12/";

    private static final QName SOAP_ADDRESS =
            new QName(SOAP_NAMESPACE, "address");
    private static final QName SOAP_BINDING =
            new QName(SOAP_NAMESPACE, "binding");

    private static final QName SOAP12_ADDRESS =
            new QName(SOAP12_NAMESPACE, "address");
    private static final QName SOAP12_BINDING =
            new QName(SOAP12_NAMESPACE, "binding");

    private WSDLParser() {
    }

    public static Collection<ServiceInfo> parseWSDL(InputStream wsdl)
            throws Exception {

        WSDLFactory wsdlFactory =
            WSDLFactory.newInstance("com.ibm.wsdl.factory.WSDLFactoryImpl");

        WSDLReader wsdlReader = wsdlFactory.newWSDLReader();
        wsdlReader.setFeature("javax.wsdl.importDocuments", false);
        wsdlReader.setFeature("com.ibm.wsdl.parseXMLSchemas", false);

        Definition definition = wsdlReader.readWSDL("", new InputSource(wsdl));

        Collection<Service> services = definition.getServices().values();

        Map<String, ServiceInfo> result = new HashMap<>();

        for (Service service : services) {
            Collection<Port> ports = service.getPorts().values();

            for (Port port : ports) {
                List<ExtensibilityElement> exts = port.getBinding().getExtensibilityElements();

                boolean soapBinding = false;
                for (ExtensibilityElement ext : exts) {
                    if (ext.getElementType().equals(SOAP_BINDING)
                            || ext.getElementType().equals(SOAP12_BINDING)) {
                        soapBinding = true;
                        break;
                    }
                }

                if (!soapBinding) {
                    continue;
                }

                exts = port.getExtensibilityElements();

                String url = null;
                for (ExtensibilityElement ext : exts) {
                    if (ext.getElementType().equals(SOAP_ADDRESS)) {
                        url = ((SOAPAddress) ext).getLocationURI();
                        break;
                    }
                    if (ext.getElementType().equals(SOAP12_ADDRESS)) {
                        url = ((SOAP12Address) ext).getLocationURI();
                        break;
                    }
                }

                List<Operation> operations = port.getBinding().getPortType().getOperations();

                for (Operation operation : operations) {
                    String title = null;
                    Element element = operation.getDocumentationElement();

                    if (element != null) {
                        NodeList nodeList = element.getChildNodes();

                        for (int i = 0; i < nodeList.getLength(); i++) {
                            Node node = nodeList.item(i);

                            if ("title".equals(node.getLocalName())) {
                                if (node.hasChildNodes()) {
                                    title = node.getFirstChild().getNodeValue();
                                    if (title != null) {
                                        title = title.trim();
                                    }
                                }
                                break;
                            }
                        }
                    }

                    result.put(operation.getName(),
                        new ServiceInfo(operation.getName(), title, url));
                }
            }
        }

        return result.values();
    }

    public static class ServiceInfo {

        public ServiceInfo(String name, String title, String url) {
            this.name = name;
            this.title = title;
            this.url = url;
        }

        public String name;
        public String title;
        public String url;
    }
}
