package ee.cyber.xroad.mediator.service.wsdlmerge.structure;

import java.util.List;

import lombok.Value;

@Value
public class PortType implements Marshallable {
    private String name;
    private List<PortOperation> operations;

    @Override
    public String getXml() throws Exception {
        // TODO Auto-generated method stub
        return null;
    }
}
