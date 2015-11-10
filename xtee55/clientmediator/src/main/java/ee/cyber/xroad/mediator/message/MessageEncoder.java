package ee.cyber.xroad.mediator.message;

import java.io.IOException;
import java.util.Map;

import ee.ria.xroad.common.message.SoapMessage;

/**
 * Declares methods of a message encoder.
 */
public interface MessageEncoder {

    /**
     * @return the content type of this encoder
     */
    String getContentType();

    /**
     * Encodes the given SOAP message.
     * @param soapMessage the SOAP message
     * @param additionalHeaders SOAP part headers
     * @throws Exception in case of any errors
     */
    void soap(SoapMessage soapMessage, Map<String, String> additionalHeaders)
            throws Exception;

    /**
     * Closes internal encoder streams.
     * @throws IOException if an I/O error occurred
     */
    void close() throws IOException;

}
