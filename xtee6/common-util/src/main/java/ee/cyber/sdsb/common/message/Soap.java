package ee.cyber.sdsb.common.message;

/**
 * General interface for SOAP messages. SoapParser returns either
 * SoapFault or SoapMessage based on the input.
 */
public interface Soap {

    /**
     * Returns the XML representation of this message.
     */
    String getXml();

}
