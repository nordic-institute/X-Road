package ee.cyber.xroad.common.message;

import java.io.InputStream;

/**
 * Soap parser interface for reading Soap message from input stream.
 */
public interface SoapParser {

    /**
     * Parses the given input stream using the provided mime type and
     * charset. Returns a Soap object.
     */
    Soap parse(String mimeType, String charset, InputStream is);

}
