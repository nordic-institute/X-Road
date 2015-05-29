package ee.ria.xroad.common.message;

import javax.xml.soap.SOAPMessage;

/**
 * Describes a Soap message that is received from the client or service.
 */
public interface SoapMessage extends Soap {

    /**
     * Returns the underlying SOAPMessage object.
     * @return SOAPMessage
     */
    SOAPMessage getSoap();

    /**
     * Returns the raw byte content of the message.
     * @return byte[]
     */
    byte[] getBytes();

    /**
     * Returns the original charset of the message.
     * @return String
     */
    String getCharset();

    /**
     * Returns true, if the message is RPC-encoded.
     * @return boolean
     */
    boolean isRpcEncoded();

    /**
     * Returns true, if the message is a request message.
     * @return boolean
     */
    boolean isRequest();

    /**
     * Returns true, if the message is a response message.
     * @return boolean
     */
    boolean isResponse();
}
