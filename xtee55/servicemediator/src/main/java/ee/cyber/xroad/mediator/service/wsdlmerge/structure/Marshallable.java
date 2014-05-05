package ee.cyber.xroad.mediator.service.wsdlmerge.structure;

/**
 * Implemented by WSDL elements that must be marshalled into XML.
 */
public interface Marshallable {
    String getXml() throws Exception;
}
