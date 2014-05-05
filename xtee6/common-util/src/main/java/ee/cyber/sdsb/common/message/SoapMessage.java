package ee.cyber.sdsb.common.message;

import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPException;

/**
 * Describes a Soap message that is received from the client or service.
 */
public interface SoapMessage extends Soap {

    /**
     * Returns the body element of the underlying SOAP message.
     */
    SOAPBody getBody() throws SOAPException;

    /**
     * Returns the original charset of the message.
     */
    String getCharset();

    /**
     * Returns true, if the message is RPC-encoded.
     */
    boolean isRpcEncoded();

    /**
     * Returns true, if the message is a request message.
     */
    boolean isRequest();

    /**
     * Returns true, if the message is a response message.
     */
    boolean isResponse();
}
