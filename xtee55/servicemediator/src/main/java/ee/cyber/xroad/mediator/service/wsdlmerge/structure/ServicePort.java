package ee.cyber.xroad.mediator.service.wsdlmerge.structure;

import java.util.List;

import javax.xml.namespace.QName;

import lombok.Value;

@Value
public class ServicePort {
    private QName binding;
    private String name;
    private List<XrdNode> xrdNodes;
}
