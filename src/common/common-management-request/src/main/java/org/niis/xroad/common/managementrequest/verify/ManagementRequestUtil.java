/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package org.niis.xroad.common.managementrequest.verify;

import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapUtils;
import ee.ria.xroad.common.request.MemberDidRegResultType;

import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.soap.SOAPElement;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPMessage;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;

import javax.xml.namespace.QName;

import java.io.IOException;
import java.util.List;

import static ee.ria.xroad.common.message.SoapHeader.NS_XROAD;
import static ee.ria.xroad.common.message.SoapHeader.PREFIX_XROAD;

/**
 * Contains utility methods for dealing with management requests.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ManagementRequestUtil {

    public static final QName REQUEST_ID = new QName(NS_XROAD, "requestId", PREFIX_XROAD);
    private static final QName MEMBER_DID_REG_RESULT = new QName(NS_XROAD, "result", PREFIX_XROAD);

    /**
     * Converts the given management request to a response, adding the specified
     * request ID.
     *
     * @param request   the request SOAP message
     * @param requestId the request ID
     * @return the response SOAP message
     */
    @ArchUnitSuppressed("NoVanillaExceptions")
    public static SoapMessageImpl toResponse(SoapMessageImpl request, int requestId) throws SOAPException, JAXBException, IOException {
        return SoapUtils.toResponse(request, soap -> addRequestId(requestId, soap));
    }

    /**
     * Converts the given management request to a response, adding the specified request ID
     * and appending the given elements after it.
     *
     * @param request   the request SOAP message
     * @param requestId the request ID
     * @param elements  elements appended to the response content after the request ID
     * @return the response SOAP message
     */
    @ArchUnitSuppressed("NoVanillaExceptions")
    public static SoapMessageImpl toResponse(SoapMessageImpl request, int requestId, List<JAXBElement<?>> elements)
            throws SOAPException, JAXBException, IOException {
        return SoapUtils.toResponse(request, soap -> {
            addRequestId(requestId, soap);
            appendElements(elements, soap);
        });
    }

    /**
     * Wraps a member DID registration result for appending to the response.
     *
     * @param result the result
     * @return the result element
     */
    public static JAXBElement<MemberDidRegResultType> memberDidRegResult(MemberDidRegResultType result) {
        return new JAXBElement<>(MEMBER_DID_REG_RESULT, MemberDidRegResultType.class, result);
    }

    private static void appendElements(List<JAXBElement<?>> elements, SOAPMessage soap) throws SOAPException {
        if (elements.isEmpty()) {
            return;
        }
        SOAPElement firstChild = SoapUtils.getFirstChild(soap.getSOAPBody());

        if (firstChild == null) {
            return;
        }

        try {
            var marshaller = ManagementRequestParser.jaxbContext().createMarshaller();
            for (JAXBElement<?> element : elements) {
                marshaller.marshal(element, firstChild);
            }
        } catch (JAXBException e) {
            throw new SOAPException("Failed to marshal response elements", e);
        }
    }

    private static void addRequestId(int requestId, SOAPMessage soap) throws SOAPException {
        log.trace("Request id: '{}'", requestId);

        SOAPElement firstChild = SoapUtils.getFirstChild(soap.getSOAPBody());

        if (firstChild == null) {
            return;
        }

        SOAPElement element = firstChild.addChildElement(REQUEST_ID);
        element.setTextContent(Integer.toString(requestId));
    }

}
