package ee.ria.xroad.common.message;

import java.io.InputStream;

/**
 * Soap parser interface for reading Soap messages from an input stream.
 */
public interface SoapParser {

    /**
     * Parses the given input stream using the provided content type.
     * Returns a Soap message.
     * @param contentType the content type of the soap message
     * @param is the input stream from which to parse the SOAP message
     * @return a Soap message parsed from the input stream
     */
    Soap parse(String contentType, InputStream is);

}
