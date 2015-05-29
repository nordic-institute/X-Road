package ee.ria.xroad.proxyui.combinedwsdl;

/**
 * XXX: Specific for V5.5!
 *
 * Thrown when WSDLs of client cannot be merged.
 */
public class InvalidWSDLCombinationException extends Exception {
    InvalidWSDLCombinationException(String message) {
        super(message);
    }
}
