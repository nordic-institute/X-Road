package ee.cyber.sdsb.common.request;

import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPException;

import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.sdsb.common.message.SoapUtils;

import static ee.cyber.sdsb.common.message.SoapHeader.NS_SDSB;
import static ee.cyber.sdsb.common.message.SoapHeader.PREFIX_SDSB;

public final class ManagementRequestUtil {

    private ManagementRequestUtil() {
    }

    public static SoapMessageImpl toResponse(SoapMessageImpl request,
            int requestId) throws Exception {
        addRequestId(requestId, request);
        return SoapUtils.toResponse(request);
    }

    private static void addRequestId(int requestId, SoapMessageImpl response)
            throws SOAPException {
        QName qname = new QName(NS_SDSB, "requestId", PREFIX_SDSB);
        
        List<SOAPElement> children = 
                SoapUtils.getChildElements(response.getSoap().getSOAPBody());
        if (children.isEmpty()) {
            return;
        }
        
        SOAPElement element = children.get(0).addChildElement(qname);
        element.setTextContent(Integer.toString(requestId));
    }

}
