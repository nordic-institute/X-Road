package ee.cyber.xroad.mediator.message;

import javax.xml.soap.SOAPMessage;

/**
 * TestSystem meta service message.
 */
public class V5XRoadTestSystem extends V5XRoadMetaServiceImpl {

    V5XRoadTestSystem(byte[] xml, String charset, SOAPMessage soap,
            boolean isRpcEncoded) throws Exception {
        super(xml, charset, isRpcEncoded
                ? new V5XRoadRpcSoapHeader() : new V5XRoadDlSoapHeader.XX(), soap);
    }

}
