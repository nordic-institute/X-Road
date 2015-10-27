package ee.ria.xroad.common.request;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;

import lombok.extern.slf4j.Slf4j;

import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapUtils;

import static ee.ria.xroad.common.message.SoapHeader.NS_XROAD;
import static ee.ria.xroad.common.message.SoapHeader.PREFIX_XROAD;

/**
 * Contains utility methods for dealing with management requests.
 */
@Slf4j
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
    public static SoapMessageImpl toResponse(
            SoapMessageImpl request, int requestId) throws Exception {
        return SoapUtils.toResponse(
                request,
                soap -> addRequestId(requestId, soap));
    }

    private static void addRequestId(int requestId, SOAPMessage soap)
            throws SOAPException {
        log.trace("Request id: '{}'", requestId);

        QName qname = new QName(NS_XROAD, "requestId", PREFIX_XROAD);
        SOAPElement firstChild = SoapUtils.getFirstChild(soap.getSOAPBody());

        if (firstChild == null) {
            return;
        }

        SOAPElement element = firstChild.addChildElement(qname);
        element.setTextContent(Integer.toString(requestId));
    }

}
