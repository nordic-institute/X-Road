package ee.cyber.sdsb.proxyui;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.wsdl.BindingOperation;
import javax.wsdl.Definition;
import javax.wsdl.Operation;
import javax.wsdl.Port;
import javax.wsdl.PortType;
import javax.wsdl.Service;
import javax.wsdl.extensions.ExtensibilityElement;
import javax.wsdl.extensions.UnknownExtensibilityElement;
import javax.wsdl.extensions.soap.SOAPAddress;
import javax.wsdl.extensions.soap12.SOAP12Address;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLReader;
import javax.xml.namespace.QName;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class WSDLParser {

    private static String SOAP_NAMESPACE =
        "http://schemas.xmlsoap.org/wsdl/soap/";

    private static String SOAP12_NAMESPACE =
        "http://schemas.xmlsoap.org/wsdl/soap12/";

    private static QName SOAP_ADDRESS = new QName(SOAP_NAMESPACE, "address");
    private static QName SOAP_BINDING = new QName(SOAP_NAMESPACE, "binding");

    private static QName SOAP12_ADDRESS = new QName(SOAP12_NAMESPACE, "address");
    private static QName SOAP12_BINDING = new QName(SOAP12_NAMESPACE, "binding");

    private static String VERSION = "version";

    public static Collection<ServiceInfo> parseWSDL(String wsdl)
            throws Exception {

        WSDLFactory wsdlFactory =
            WSDLFactory.newInstance("com.ibm.wsdl.factory.WSDLFactoryImpl");

        WSDLReader wsdlReader = wsdlFactory.newWSDLReader();
        wsdlReader.setFeature("javax.wsdl.importDocuments", false);
        wsdlReader.setFeature("com.ibm.wsdl.parseXMLSchemas", false);

        Definition definition = wsdlReader.readWSDL(wsdl);

        Collection<Service> services = (Collection<Service>)
            definition.getServices().values();

        Map<String, ServiceInfo> result = new HashMap<>();

        for (Service service : services) {

            Collection<Port> ports = (Collection<Port>)
                service.getPorts().values();

            for (Port port : ports) {
                List<ExtensibilityElement> exts = (List<ExtensibilityElement>)
                    port.getBinding().getExtensibilityElements();

                boolean soapBinding = false;
                for (ExtensibilityElement ext : exts) {
                    if (ext.getElementType().equals(SOAP_BINDING) ||
                        ext.getElementType().equals(SOAP12_BINDING)) {
                        soapBinding = true;
                        break;
                    }
                }

                if (!soapBinding) {
                    continue;
                }

                exts = (List<ExtensibilityElement>)
                    port.getExtensibilityElements();

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

                List<BindingOperation> operations = (List<BindingOperation>)
                    port.getBinding().getBindingOperations();

                for (BindingOperation operation : operations) {
                    String title = getChildValue("title",
                        operation.getOperation().getDocumentationElement());

                    String version = null;
                    exts = (List<ExtensibilityElement>)
                        operation.getExtensibilityElements();

                    for (ExtensibilityElement ext : exts) {
                        if (ext.getElementType().getLocalPart().equals(VERSION)) {
                            version = getValue(
                                ((UnknownExtensibilityElement) ext).getElement());
                            break;
                        }
                    }

                    result.put(operation.getName(), new ServiceInfo(
                        operation.getName(), title, url, version));
                }
            }
        }

        return result.values();
    }
    
    private static String getChildValue(String childName, Element element) {
        if (element == null) {
            return null;
        }

        NodeList nodeList = element.getChildNodes();

        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
                            
            if (childName.equals(node.getLocalName())) {
                return getValue(node);
            }
        }

        return null;
    }

    private static String getValue(Node node) {
        if (node.hasChildNodes()) {
            String value = node.getFirstChild().getNodeValue();

            if (value != null) {
                value = value.trim();
            }

            return value;
        }

        return null;
    }

    public static class ServiceInfo {

        public ServiceInfo(
            String name, String title, String url, String version) {

            this.name = name;
            this.title = title;
            this.url = url;
            this.version = version;
        }

        public String name;
        public String title;
        public String url;
        public String version;
    }
}
