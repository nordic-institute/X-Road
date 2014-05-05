package ee.cyber.xroad.mediator.service.wsdlmerge.structure;

import java.util.List;

import lombok.Value;

@Value
public class BindingOperation {
    private String name;
    private String version;
    private List<XrdNode> xrdNodes;
}
