/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
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
