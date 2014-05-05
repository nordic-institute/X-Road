package ee.cyber.xroad.mediator.service.wsdlmerge.merger;

import java.io.InputStream;
import java.util.List;

import lombok.Getter;

import ee.cyber.xroad.mediator.service.wsdlmerge.structure.WSDL;

/**
 * Creates combined WSDL out of parsed artifacts.
 */
class WSDLMerger {
    @Getter
    private InputStream mergedWsdlAsStream;

    WSDLMerger(List<WSDL> wsdls) {
        // TODO: Implement all the merging logic!
    }
}
