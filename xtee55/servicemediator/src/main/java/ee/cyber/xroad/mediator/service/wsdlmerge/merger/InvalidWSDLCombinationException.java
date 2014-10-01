package ee.cyber.xroad.mediator.service.wsdlmerge.merger;

import java.io.IOException;

/**
 * Thrown by WSDL merger when WSDL-s cannot be combined.
 */
public class InvalidWSDLCombinationException extends IOException {
    public InvalidWSDLCombinationException(String message) {
        super(message);
    }
}
