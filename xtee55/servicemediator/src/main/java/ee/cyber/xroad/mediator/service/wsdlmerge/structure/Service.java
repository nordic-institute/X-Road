package ee.cyber.xroad.mediator.service.wsdlmerge.structure;

import java.util.List;

import lombok.Value;

@Value
public class Service {
    private String name;
    private List<ServicePort> ports;
}
