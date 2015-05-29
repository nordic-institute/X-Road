package ee.cyber.xroad.mediator.service.wsdlmerge.merger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * Provides WSDL stream for processing.
 */
public class WSDLProvider {

    InputStream getWsdl(String wsdlUrl) throws IOException {
        return new URL(wsdlUrl).openStream();
    }
}
