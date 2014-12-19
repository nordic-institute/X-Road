package ee.cyber.xroad.mediator.message;

import javax.xml.soap.SOAPMessage;

public class XRoadTestSystem extends XRoadMetaServiceImpl {

    XRoadTestSystem(byte[] xml, String charset, SOAPMessage soap,
            boolean isRpcEncoded) throws Exception {
        super(xml, charset, isRpcEncoded
                ? new XRoadRpcSoapHeader() : new XRoadDlSoapHeader.XX(), soap);
    }

}
