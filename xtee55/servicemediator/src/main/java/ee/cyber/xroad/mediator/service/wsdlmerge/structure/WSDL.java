package ee.cyber.xroad.mediator.service.wsdlmerge.structure;

import java.util.List;

import lombok.Value;

/**
 * Description of single WSDL
 */
@Value
public class WSDL {
    private List<XrdNode> schemaElements;
    private List<Message> messages;
    private List<PortType> portTypes;
    private List<Binding> bindings;
    private List<Service> services;
    private boolean doclit;
    private String xrdNamespace;
}
