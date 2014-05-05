package ee.cyber.xroad.mediator.service.wsdlmerge.structure;

import java.util.List;

import javax.xml.namespace.QName;

import lombok.Value;

@Value
public class Binding {
    private String name;
    private QName type;
    private List<BindingOperation> operations;
}
