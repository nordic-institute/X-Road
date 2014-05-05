package ee.cyber.xroad.mediator.service.wsdlmerge.structure;

import java.util.List;

import javax.xml.namespace.QName;

import lombok.Value;

@Value
public class PortOperation {
    private String name;
    private QName input;
    private QName output;
    private List<XrdNode> documentation;
}
