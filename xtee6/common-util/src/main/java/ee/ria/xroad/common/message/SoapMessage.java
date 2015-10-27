package ee.ria.xroad.common.message;

import javax.xml.soap.SOAPMessage;

import org.eclipse.jetty.http.MimeTypes;

/**
 * Describes a Soap message that is received from the client or service.
 */
public interface SoapMessage extends Soap {

    /**
     * @return the underlying SOAPMessage object.
     */
    SOAPMessage getSoap();

    /**
     * @return the raw byte content of the message.
     */
    byte[] getBytes();

    /**
     * @return the original charset of the message.
     */
    String getCharset();

    /**
     * @return true, if the message is RPC-encoded.
     */
    boolean isRpcEncoded();

    /**
     * @return true, if the message is a request message.
     */
    boolean isRequest();

    /**
     * @return true, if the message is a response message.
     */
    boolean isResponse();

    /**
     * @return the content type of the SOAP message
     */
    default String getContentType() {
        return MimeTypes.TEXT_XML;
    }
}
