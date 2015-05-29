package ee.ria.xroad.common.request;

import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPException;

import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapUtils;

import static ee.ria.xroad.common.message.SoapHeader.NS_XROAD;
import static ee.ria.xroad.common.message.SoapHeader.PREFIX_XROAD;

/**
 * Contains utility methods for dealing with management requests.
 */
public final class ManagementRequestUtil {

    private ManagementRequestUtil() {
    }

    /**
     * Converts the given management request to a response, adding the specified
     * request ID.
     * @param request the request SOAP message
     * @param requestId the request ID
     * @return the response SOAP message
     * @throws Exception in case of any errors
     */
    public static SoapMessageImpl toResponse(SoapMessageImpl request,
            int requestId) throws Exception {
        addRequestId(requestId, request);
        return SoapUtils.toResponse(request);
    }

    private static void addRequestId(int requestId, SoapMessageImpl response)
            throws SOAPException {
        QName qname = new QName(NS_XROAD, "requestId", PREFIX_XROAD);

        List<SOAPElement> children =
                SoapUtils.getChildElements(response.getSoap().getSOAPBody());
        if (children.isEmpty()) {
            return;
        }

        SOAPElement element = children.get(0).addChildElement(qname);
        element.setTextContent(Integer.toString(requestId));
    }

}
