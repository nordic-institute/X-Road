package ee.ria.xroad.common.message;

import java.io.InputStream;

/**
 * Soap parser interface for reading Soap messages from an input stream.
 */
public interface SoapParser {

    /**
     * Parses the given input stream using the provided mime type and
     * charset. Returns a Soap object.
     * @param mimeType expected mime type of the input stream
     * @param charset expected charset of the input stream
     * @param is the input stream from which to parse the SOAP message
     * @return a Soap message parsed from the input stream
     */
    Soap parse(String mimeType, String charset, InputStream is);

}
