package ee.cyber.xroad.mediator.service.wsdlmerge.merger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.xml.namespace.QName;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import ee.cyber.xroad.mediator.service.wsdlmerge.TestNS;
import ee.cyber.xroad.mediator.service.wsdlmerge.structure.*;
import ee.cyber.xroad.mediator.service.wsdlmerge.structure.binding.Binding;
import ee.cyber.xroad.mediator.service.wsdlmerge.structure.binding.BindingOperation;
import ee.cyber.xroad.mediator.service.wsdlmerge.structure.binding.DoclitBinding;
import ee.cyber.xroad.mediator.service.wsdlmerge.structure.binding.DoclitBindingOperation;

/**
 * Creates mock WSDL for testing.
 */
@Slf4j
public class MockWSDLCreator {
    private List<String> serviceNames;
    private String version;
    private String suffix;
    private String xrdNs;
    private String targetNs = TestNS.XRDDL_TNS;
    private String dbName = "";

    private String firstServiceName;

    /**
     * Creates basic mock WSDL.
     *
     * @param serviceName service name
     * @param version service version
     * @param suffix suffix for service name
     * @param xrdNs X-Road namespace used.
     */
    public MockWSDLCreator(String serviceName,
            String version, String suffix, String xrdNs) {
        this(version, suffix, xrdNs);

        this.serviceNames = Collections.singletonList(serviceName);
    }

    /**
     * Creates mock WSDL with multiple service names, target namespace
     * and database name.
     *
     * @param serviceNames service name.
     * @param version service version.
     * @param suffix suffix for service name.
     * @param xrdNs X-Road namespace used.
     * @param targetNs target namespace.
     * @param dbName database name.
     */
    public MockWSDLCreator(List<String> serviceNames,
            String version, String suffix, String xrdNs, String targetNs,
            String dbName) {
        this(version, suffix, xrdNs);

        this.serviceNames = serviceNames;
        this.targetNs = targetNs;
        this.dbName = dbName;
    }

    private MockWSDLCreator(String version, String suffix, String xrdNs) {
        this.version = version;
        this.suffix = suffix;
        this.xrdNs = xrdNs;
    }

    /**
     * Creates mock WSDL.
     *
     * @return mock WSDL created.
     */
    public WSDL getWSDL() {
        firstServiceName = serviceNames.get(0);

        List<XrdNode> schemaElements = getSchemaElements();

        List<Message> messages = getMessages();

        String portName = getPortName();
        List<PortType> portTypes = getPortTypes(portName);

        String bindingName = getBindingName();
        List<Binding> bindings = getBindings(portName, bindingName);

        List<Service> services = getServices(bindingName);

        WSDL result = new WSDL(
                schemaElements,
                messages,
                portTypes,
                bindings,
                services,
                xrdNs,
                targetNs,
                dbName);

        try {
            log.trace("Mock WSDL created:\n{}", result.getXml());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    private List<XrdNode> getSchemaElements() {
        List<XrdNode> schemaElements = new ArrayList<>();

        for (String each : serviceNames) {
            String requestName = getRequestName(each, suffix);
            String responseName = getResponseName(each, suffix);

            schemaElements.add(new MockXrdNode(
                    requestName, getRequestElementName(each, suffix)));
            schemaElements.add(new MockXrdNode(
                    responseName, getResponseElementName(each, suffix)));

        }
        return schemaElements;
    }

    private List<Message> getMessages() {
        List<Message> messages = new ArrayList<>();
        messages.add(getV5XRoadStandardHeader());

        for (String each : serviceNames) {
            String requestName = getRequestName(each, suffix);
            String responseName = getResponseName(each, suffix);

            MessagePart requestPart = new MessagePart(
                    requestName,
                    new QName(targetNs, requestName),
                    null);
            MessagePart responsePart = new MessagePart(
                    responseName,
                    new QName(targetNs, responseName),
                    null);

            Message requestMessage = new Message(
                    requestName, Arrays.asList(requestPart), false);
            Message responseMessage = new Message(
                    responseName, Arrays.asList(responsePart), false);

            messages.add(requestMessage);
            messages.add(responseMessage);
        }
        return messages;
    }

    private List<PortType> getPortTypes(String portName) {
        List<Marshallable> opDoc = new ArrayList<>();
        opDoc.add(new MockXrdNode(null, "<opDoc/>"));

        List<PortOperation> portOps = new ArrayList<>();

        for (String each : serviceNames) {
            String requestName = getRequestName(each, suffix);
            String responseName = getResponseName(each, suffix);

            PortOperation getRandomOperation = new PortOperation(
                    each,
                    new QName(targetNs, requestName),
                    new QName(targetNs, responseName),
                    opDoc);

            portOps.add(getRandomOperation);
        }

        PortType portType = new PortType(
                portName, portOps);

        return Arrays.asList(portType);
    }

    private List<Binding> getBindings(String portName, String bindingName) {
        List<Marshallable> bindingOpXrdNodes = new ArrayList<>();
        bindingOpXrdNodes.add(new MockXrdNode(null, "<bindingOpNode/>"));
        List<BindingOperation> bindingOps = new ArrayList<>();

        for (String each : serviceNames) {
            // Here it doesn't really matter if it is rpc or doclit.
            BindingOperation bindingOp = new DoclitBindingOperation(
                    each,
                    version,
                    bindingOpXrdNodes);

            bindingOps.add(bindingOp);
        }

        Binding binding = new DoclitBinding(
                bindingName,
                new QName(targetNs, portName),
                bindingOps);

        return Arrays.asList(binding);
    }

    private List<Service> getServices(String bindingName) {
        List<Marshallable> xrdNodes = new ArrayList<>();
        xrdNodes.add(new MockXrdNode(null, "<servicePortNode/>"));

        ServicePort servicePort = new ServicePort(
                new QName(targetNs, bindingName),
                getServicePortName(),
                xrdNodes);
        List<ServicePort> servicePorts = Arrays.asList(servicePort);

        Service service = new Service("firstDatabaseService", servicePorts);

        return Arrays.asList(service);
    }

    private String getRequestName(String serviceName, String requestSuffix) {
        return String.format("%sRequest%s", serviceName, requestSuffix);
    }

    private String getResponseName(String serviceName, String responseSuffix) {
        return String.format("%sResponse%s", serviceName, responseSuffix);
    }

    private String getRequestElementName(
            String serviceName, String requestSuffix) {
        return String.format("<%sRequestElem%s/>", serviceName, requestSuffix);
    }

    private String getResponseElementName(
            String serviceName, String responseSuffix) {
        return String.format("<%sResponseElem%s/>", serviceName, responseSuffix);
    }

    private String getPortName() {
        ensureFirstServiceNameFilled();

        return String.format("%sPort", firstServiceName);
    }

    private String getBindingName() {
        ensureFirstServiceNameFilled();

        return String.format("%sBinding", firstServiceName);
    }

    private String getServicePortName() {
        ensureFirstServiceNameFilled();

        return String.format("%sServicePort", firstServiceName);
    }

    private void ensureFirstServiceNameFilled() {
        if (StringUtils.isBlank(firstServiceName)) {
            throw new IllegalStateException(
                    "First service name must not be blank by this point!");
        }
    }

    /**
     * Returns X-Road V5 standard header.
     *
     * @return X-Road V5 standard header.
     */
    public static Message getV5XRoadStandardHeader() {
        MessagePart consumer = new MessagePart(
                "consumer",
                new QName(TestNS.XROAD_NS, "consumer"),
                null);
        MessagePart producer = new MessagePart(
                "producer",
                new QName(TestNS.XROAD_NS, "producer"),
                null);
        MessagePart userId = new MessagePart(
                "userId",
                new QName(TestNS.XROAD_NS, "userId"),
                null);
        MessagePart service = new MessagePart(
                "service",
                new QName(TestNS.XROAD_NS, "service"),
                null);
        MessagePart id = new MessagePart(
                "id",
                new QName(TestNS.XROAD_NS, "id"),
                null);

        return new Message(
                "standardheader",
                Arrays.asList(
                        consumer,
                        producer,
                        userId,
                        service,
                        id),
                true);
    }
}
