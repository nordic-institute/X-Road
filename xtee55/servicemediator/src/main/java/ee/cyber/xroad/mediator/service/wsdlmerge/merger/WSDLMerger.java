package ee.cyber.xroad.mediator.service.wsdlmerge.merger;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Node;

import ee.cyber.xroad.mediator.service.wsdlmerge.structure.*;
import ee.cyber.xroad.mediator.service.wsdlmerge.structure.binding.Binding;
import ee.cyber.xroad.mediator.service.wsdlmerge.structure.binding.BindingOperation;
import ee.cyber.xroad.mediator.service.wsdlmerge.structure.binding.DoclitBinding;

/**
 * Creates combined WSDL out of parsed artifacts.
 */
class WSDLMerger {

    @Getter
    private WSDL mergedWsdl;

    private List<WSDL> wsdls;

    private List<XrdNode> mergedSchemaElements = new ArrayList<>();
    private List<Message> mergedMessages = new ArrayList<>();
    private List<PortOperation> mergedPortOps = new ArrayList<>();
    private List<BindingOperation> mergedBindingOps = new ArrayList<>();

    private String mergedPortTypeName = null;
    private String mergedBindingName = null;
    private QName mergedBindingType = null;
    private Service mergedService = null;

    private String oldTargetNamespace = null;

    private String newTargetNamespace = null;
    private String databaseV5Name;

    private Map<String, BindingOperation> nameToBindingOp = new HashMap<>();

    private Set<String> xrdNamespaces = new HashSet<>();

    /** Just a placeholder for binding operations' cleanup. */
    private List<BindingOperation> tempBindingOps = null;
    private boolean isStandardHeaderAdded = false;

    WSDLMerger(List<WSDL> wsdls, String databaseV5Name)
            throws InvalidWSDLCombinationException {
        this.wsdls = wsdls;
        this.databaseV5Name = databaseV5Name;

        merge();
    }

    private void merge() throws InvalidWSDLCombinationException {
        extractInputWsdls();

        validateXrdNamespaces();

        organizeBindingOps();
        validateSchemaElementNames();

        setUpNewTargetNamespace();
        updateMergedServiceProducerName();

        createMergedWsdl();
    }

    private void extractInputWsdls() {
        for (int i = 0; i < wsdls.size(); i++) {
            boolean first = i == 0;
            WSDL eachWsdl = wsdls.get(i);

            mergedSchemaElements.addAll(eachWsdl.getSchemaElements());
            addMessages(eachWsdl);

            xrdNamespaces.add(eachWsdl.getXrdNamespace());

            for (PortType eachPort : eachWsdl.getPortTypes()) {
                mergedPortOps.addAll(eachPort.getOperations());
            }

            for (Binding eachBinding : eachWsdl.getBindings()) {
                mergedBindingOps.addAll(eachBinding.getOperations());
            }

            if (first) {
                oldTargetNamespace = eachWsdl.getTargetNamespace();

                PortType firstPortType = eachWsdl.getPortTypes().get(0);
                mergedPortTypeName = firstPortType.getName();

                Binding firstBinding = eachWsdl.getBindings().get(0);
                mergedBindingName = firstBinding.getName();
                mergedBindingType = firstBinding.getType();

                mergedService = eachWsdl.getServices().get(0);
            }
        }
    }

    private void addMessages(WSDL wsdl) {
        for (Message each : wsdl.getMessages()) {
            addMessageIfPossible(each);
        }
    }

    /**
     * Ensures that standard header is added only once.
     */
    private void addMessageIfPossible(Message each) {
        if (each.isXrdStandardHeader()) {
            if (isStandardHeaderAdded) {
                return;
            } else {
                mergedMessages.add(each);
                isStandardHeaderAdded = true;
            }
        } else {
            mergedMessages.add(each);
        }
    }

    private void validateXrdNamespaces() throws InvalidWSDLCombinationException {
        if (xrdNamespaces.size() == 1) {
            return;
        }

        String errorMsg = String.format(
                "WSDLs must use the same X-Road namespace. Found: %s",
                StringUtils.join(xrdNamespaces, ", "));
        throw new InvalidWSDLCombinationException(errorMsg);
    }

    private void organizeBindingOps() throws InvalidWSDLCombinationException {
        tempBindingOps = new ArrayList<>(mergedBindingOps.size());

        for (BindingOperation each : mergedBindingOps) {
            String opName = each.getName();
            BindingOperation bindingOpWithSameName =
                    nameToBindingOp.get(opName);

            if (areBindingOpsIdentical(each, bindingOpWithSameName)) {
                String errorMsg = String.format(
                        "Operation with name '%s' and version '%s' exists "
                                + "multiple times in WSDL-s, "
                                + "inspect mergeable WSDL-s.",
                                opName, each.getVersion());
                throw new InvalidWSDLCombinationException(errorMsg);
            }

            addNewestOpVersion(bindingOpWithSameName, each);
            tempBindingOps.add(each);
        }

        mergedBindingOps = tempBindingOps;
    }

    private void validateSchemaElementNames()
            throws InvalidWSDLCombinationException {
        Set<String> existingNames = new HashSet<>();

        for (XrdNode each : mergedSchemaElements) {
            if (!existingNames.add(each.getName())) {
                throw new InvalidWSDLCombinationException(
                        "Merging WSDLs failed due to "
                                + "conflicting top level Schema elements: '"
                                + each.getName() + "'");
            }
        }

    }

    /**
     * Adds newest version of the operation.
     */
    private void addNewestOpVersion(
            BindingOperation existingOp, BindingOperation newOp) {
        if (existingOp == null) {
            nameToBindingOp.put(newOp.getName(), newOp);
            return;
        }

        if (isVersionUpdated(existingOp, newOp)) {
            nameToBindingOp.put(newOp.getName(), newOp);
            cleanupOldOpVersion(existingOp);
        }
    }

    private boolean isVersionUpdated(
            BindingOperation existingOp, BindingOperation newOp) {
        int existingVersion = getRequestVersion(existingOp);
        int newVersion = getRequestVersion(newOp);

        return newVersion > existingVersion;
    }

    private int getRequestVersion(BindingOperation existingOp) {
        String version = existingOp.getVersion();

        if (StringUtils.isBlank(version) || !version.startsWith("v")) {
            return -1;
        }

        String[] versionParts = version.split("v");
        String rawVersionNo = versionParts[1];

        if (!StringUtils.isNumeric(rawVersionNo)) {
            throw new RuntimeException("Malformed version: " + version);
        }

        return Integer.parseInt(rawVersionNo);
    }

    private boolean areBindingOpsIdentical(BindingOperation firstOp,
            BindingOperation secondOp) {
        if (firstOp == null || secondOp == null) {
            return false;
        }

        return StringUtils.equals(firstOp.getName(), secondOp.getName())
                && StringUtils.equals(firstOp.getVersion(),
                        secondOp.getVersion());
    }

    // -- Old operation cleanup logic - start ---

    private void cleanupOldOpVersion(BindingOperation oldOp) {
        tempBindingOps.remove(oldOp);

        PortOperation removablePortOp = getPortOpByName(oldOp.getName());
        mergedPortOps.remove(removablePortOp);

        Message removableInputMessage =
                getMessageByQName(removablePortOp.getInput());
        Message removableOutputMessage =
                getMessageByQName(removablePortOp.getOutput());
        mergedMessages.remove(removableInputMessage);
        mergedMessages.remove(removableOutputMessage);

        cleanupSchemaElements(removableInputMessage.getParts());
        cleanupSchemaElements(removableOutputMessage.getParts());
    }

    private void cleanupSchemaElements(List<MessagePart> parts) {
        for (MessagePart eachPart : parts) {
            mergedSchemaElements.remove(
                    getSchemaElementByQName(eachPart.getElement()));
            mergedSchemaElements.remove(
                    getSchemaElementByQName(eachPart.getType()));
        }
    }

    private Message getMessageByQName(QName qName) {
        for (Message each : mergedMessages) {
            if (StringUtils.equals(qName.getLocalPart(), each.getName())) {
                return each;
            }
        }

        throw new IllegalArgumentException(
                "Message with QName '" + qName + "' not found.");
    }

    private XrdNode getSchemaElementByQName(QName qName) {
        if (qName == null) {
            return null;
        }

        for (XrdNode each : mergedSchemaElements) {
            if (StringUtils.equals(qName.getLocalPart(), each.getName())) {
                return each;
            }
        }

        return null;
    }

    private PortOperation getPortOpByName(String name) {
        for (PortOperation each : mergedPortOps) {
            if (StringUtils.equals(name, each.getName())) {
                return each;
            }
        }

        throw new IllegalArgumentException(
                "Port operation with name '" + name + "' not found.");
    }

    // -- Old operation cleanup logic - end ---

    // -- Functions related to new TNS - start ---

    private void setUpNewTargetNamespace() {
        newTargetNamespace = getMergedWsdlTargetNamespace();

        setUpNewTargetNamespaceInMessages();
        setUpNewTargetNamespacesInPortOps();
        setUpNewTargetNamespacesInBindingType();
        setUpNewTargetNamespacesInServicePortBinding();
    }

    private void setUpNewTargetNamespaceInMessages() {
        List<Message> newMessages = new ArrayList<>(mergedMessages.size());

        for (Message eachMessage : mergedMessages) {
            if (eachMessage.isXrdStandardHeader()) {
                newMessages.add(eachMessage);
                continue;
            }

            List<MessagePart> parts = eachMessage.getParts();
            List<MessagePart> newParts = new ArrayList<>(parts.size());

            for (MessagePart eachPart : parts) {
                addMessagePartWithNewNamespace(newParts, eachPart);
            }

            newMessages.add(new Message(
                    eachMessage.getName(),
                    newParts,
                    eachMessage.isXrdStandardHeader()));
        }

        mergedMessages = newMessages;
    }

    private void addMessagePartWithNewNamespace(List<MessagePart> newParts,
            MessagePart eachPart) {
        QName oldElement = eachPart.getElement();
        QName oldType = eachPart.getType();

        QName newElement = oldElement == null ? null
                : new QName(newTargetNamespace,
                        oldElement.getLocalPart());

        QName newType = oldType == null ? null
                : new QName(newTargetNamespace,
                        oldType.getLocalPart());

        MessagePart newPart = new MessagePart(
                eachPart.getName(), newElement, newType);

        newParts.add(newPart);
    }

    private void setUpNewTargetNamespacesInPortOps() {
        List<PortOperation> newPortOps = new ArrayList<>(mergedPortOps.size());

        for (PortOperation eachOp : mergedPortOps) {
            addPortOpWithNewNamespace(newPortOps, eachOp);
        }

        mergedPortOps = newPortOps;
    }

    private void addPortOpWithNewNamespace(List<PortOperation> newPortOps,
            PortOperation eachOp) {
        QName oldInput = eachOp.getInput();
        QName oldOutput = eachOp.getOutput();

        PortOperation newOp = new PortOperation(
                eachOp.getName(),
                new QName(
                        newTargetNamespace,
                        oldInput.getLocalPart()),
                new QName(
                        newTargetNamespace,
                        oldOutput.getLocalPart()),
                eachOp.getDocumentation());

        newPortOps.add(newOp);
    }

    private void setUpNewTargetNamespacesInBindingType() {
        mergedBindingType = new QName(
                newTargetNamespace,
                mergedBindingType.getLocalPart());
    }

    private void setUpNewTargetNamespacesInServicePortBinding() {
        List<ServicePort> oldPorts = mergedService.getPorts();
        List<ServicePort> newPorts = new ArrayList<>(oldPorts.size());

        for (ServicePort eachPort : oldPorts) {
            QName oldBinding = eachPort.getBinding();
            QName newBinding = new QName(
                    newTargetNamespace, oldBinding.getLocalPart());

            ServicePort newPort = new ServicePort(
                    newBinding, eachPort.getName(), eachPort.getXrdNodes());

            newPorts.add(newPort);
        }

        mergedService = new Service(mergedService.getName(), newPorts);
    }

    private String getMergedWsdlTargetNamespace() {
        return String.format(
                getDoclitTargetNamespaceTemplate(), databaseV5Name);
    }

    private String getDoclitTargetNamespaceTemplate() {
        if (oldTargetNamespace.contains("x-road.ee")) {
            return "http://%s.x-road.ee/producer";
        } else if (oldTargetNamespace.contains("x-road.eu")) {
            return "http://%s.x-road.eu/producer";
        } else if (oldTargetNamespace.contains("x-rd.net")) {
            return "http://%s.x-rd.net/producer";
        }

        throw new RuntimeException(
                "Invalid doclit target namespace: " + oldTargetNamespace);
    }

    // -- Functions related to new TNS - end ---

    private void updateMergedServiceProducerName() {
        for (ServicePort eachPort : mergedService.getPorts()) {
            for (Marshallable eachNode : eachPort.getXrdNodes()) {
                changeProducerIfNecessary(eachNode);
            }
        }
    }

    private void changeProducerIfNecessary(Marshallable rawNode) {
        if (!(rawNode instanceof XrdNode)) {
            return;
        }

        Node innerNode = ((XrdNode) rawNode).getNode();

        if (!isXrdAddressNode(innerNode)) {
            return;
        }

        innerNode.getAttributes().getNamedItem("producer")
                .setTextContent(databaseV5Name);
    }

    private boolean isXrdAddressNode(Node node) {
        if (node == null) {
            return false;
        }

        return StringUtils.equals("address", node.getLocalName())
                && StringUtils.equals(getXrdNamespace(),
                        node.getNamespaceURI());
    }

    private void createMergedWsdl() {
        List<PortType> mergedPortTypes = Collections.singletonList(
                new PortType(mergedPortTypeName, mergedPortOps));

        Binding mergedBinding = new DoclitBinding(
                        mergedBindingName,
                        mergedBindingType,
                        mergedBindingOps);

        List<Binding> mergedBindings = Collections.singletonList(mergedBinding);

        List<Service> mergedServices = Collections.singletonList(mergedService);

        String xrdNamespace = getXrdNamespace();

        mergedWsdl = new WSDL(
                mergedSchemaElements,
                mergedMessages,
                mergedPortTypes,
                mergedBindings,
                mergedServices,
                xrdNamespace,
                newTargetNamespace,
                databaseV5Name);
    }

    private String getXrdNamespace() {
        return (String) xrdNamespaces.toArray()[0];
    }
}
