package ee.cyber.xroad.mediator.service.wsdlmerge.merger;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import ee.cyber.xroad.mediator.service.wsdlmerge.structure.XrdNode;

@EqualsAndHashCode(callSuper = false)
class MockXrdNode extends XrdNode {
    @Getter
    private final String name;
    private final String content;

    public MockXrdNode(String name, String content) {
        super(null);
        this.name = name;
        this.content = content;
    }

    @Override
    public String getXml() throws Exception {
        return content;
    }
}
