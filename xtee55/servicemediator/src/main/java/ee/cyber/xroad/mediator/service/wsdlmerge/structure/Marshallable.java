package ee.cyber.xroad.mediator.service.wsdlmerge.structure;

/**
 * Implemented by WSDL elements that must be marshalled into XML.
 */
public interface Marshallable {
    /**
     * Returns XML from marshallable WSDL element.
     *
     * @return XML as String.
     * @throws Exception thrown when marshalling element fails.
     */
    String getXml() throws Exception;
}
