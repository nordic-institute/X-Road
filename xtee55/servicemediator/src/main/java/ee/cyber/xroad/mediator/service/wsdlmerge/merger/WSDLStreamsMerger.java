package ee.cyber.xroad.mediator.service.wsdlmerge.merger;

import java.io.InputStream;
import java.util.List;

import org.apache.commons.lang.NotImplementedException;

/**
 * Takes streams of input WSDL-s and converts them to the stream including
 * merged WSDL bytes.
 */
public class WSDLStreamsMerger {
    InputStream merge(List<InputStream> inputWsdls) {
        // TODO: Parse streams taking order number into consideration.
        // TODO: Merge WSDL-s from parsed artifacts.
        throw new NotImplementedException(
                "Merging WSDL input streams is not yet implemented");
    }
}
