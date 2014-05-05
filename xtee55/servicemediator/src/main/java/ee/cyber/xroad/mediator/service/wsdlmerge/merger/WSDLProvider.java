package ee.cyber.xroad.mediator.service.wsdlmerge.merger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class WSDLProvider {

    InputStream getWsdl(String wsdlUrl) throws IOException {
        return new URL(wsdlUrl).openStream();
    }
}
