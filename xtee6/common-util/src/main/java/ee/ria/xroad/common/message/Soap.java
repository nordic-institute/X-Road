package ee.ria.xroad.common.message;

/**
 * General interface for SOAP messages. SoapParser returns either
 * SoapFault or SoapMessage based on the input.
 */
public interface Soap {

    /**
     * Returns the XML representation of this message.
     * @return String
     * @throws Exception in case of errors
     */
    String getXml() throws Exception;

}
