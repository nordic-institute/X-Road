/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
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
import ee.ria.xroad.common.request.AuthCertDeletionRequestType;
import ee.ria.xroad.common.request.AuthCertRegRequestType;
import ee.ria.xroad.common.request.ClientRequestType;
import ee.ria.xroad.common.request.ObjectFactory;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.managementrequest.model.ManagementRequestType;
import org.w3c.dom.Node;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.soap.SOAPException;

import java.io.UnsupportedEncodingException;

/**
 * Parser for management requests.
 */
@Slf4j
public final class ManagementRequestParser {
    private static final JAXBContext JAXB_CTX;

    private ManagementRequestParser() {
    }

    /**
     * Parses an authentication certificate registration request.
     *
     * @param message the request SOAP message
     * @return the authentication certificate registration request
     * @throws Exception in case of any errors
     */
    public static AuthCertRegRequestType parseAuthCertRegRequest(SoapMessageImpl message) throws SOAPException {
        var serviceCode = ManagementRequestType.AUTH_CERT_REGISTRATION_REQUEST.getServiceCode();
        if (log.isTraceEnabled()) {
            try {
                log.trace("parse(expectedNodeName: {}, message: {})", serviceCode, message.getXml());
            } catch (UnsupportedEncodingException e) {
                log.trace("Unexpected error", e);
            }
        }

        Node node = message.getSoap().getSOAPBody().getFirstChild();
        if (node == null) {
            log.error("Message is missing content node");
            throw new SOAPException("SoapMessage has no content");
        }

        String nodeName = node.getLocalName();
        if (!serviceCode.equalsIgnoreCase(nodeName)) {
            log.error("Content node name ({}) does not match expected name ({})", nodeName, serviceCode);
            throw new SOAPException("Unexpected content: " + nodeName);
        }

        try {
            Unmarshaller um = JAXB_CTX.createUnmarshaller();
            return um.unmarshal(node, AuthCertRegRequestType.class).getValue();
        } catch (JAXBException e) {
            String msg = String.format("Failed to parse '%s'", serviceCode);
            log.error(msg);
            throw new SOAPException(msg, e);
        }
    }

    /**
     * Parses an authentication certificate deletion request.
     * @param message the request SOAP message
     * @return the authentication certificate deletion request
     * @throws Exception in case of any errors
     */
    public static AuthCertDeletionRequestType parseAuthCertDeletionRequest(
            SoapMessageImpl message) throws Exception {
        return parse(message, ManagementRequestType.AUTH_CERT_DELETION_REQUEST.getServiceCode());
    }

    /**
     * Parses a management request with the given name.
     *
     * @param message               the request SOAP message
     * @param managementRequestName name of the management request
     * @return the management request
     * @throws Exception in case of any errors
     */
    public static ClientRequestType parseRequest(SoapMessageImpl message, String managementRequestName)
            throws Exception {
        return parse(message, managementRequestName);
    }

    private static <T> T parse(SoapMessageImpl message, String expectedNodeName)
            throws Exception {

        if (log.isDebugEnabled()) {
            log.debug("parse(expectedNodeName: {}, message: {})",
                    expectedNodeName, message.getXml());
        }

        Node node = message.getSoap().getSOAPBody().getFirstChild();
        if (node == null) {
            log.error("Message is missing content node");

            throw new RuntimeException("SoapMessage has no content");
        }

        String nodeName = node.getLocalName();
        if (!expectedNodeName.equalsIgnoreCase(nodeName)) {
            log.error("Content node name ({}) does not match "
                    + "expected name ({})", nodeName, expectedNodeName);

            throw new RuntimeException("Unexpected content: " + nodeName);
        }

        Unmarshaller um = JAXB_CTX.createUnmarshaller();
        try {
            @SuppressWarnings("unchecked")
            JAXBElement<T> req = (JAXBElement<T>) um.unmarshal(node);
            return req.getValue();
        } catch (JAXBException e) {
            String m = String.format("Failed to parse '%s'", expectedNodeName);

            log.error(m, e);

            throw new RuntimeException(m, e);
        }
    }

    static {
        try {
            JAXB_CTX = JAXBContext.newInstance(ObjectFactory.class);
        } catch (JAXBException e) {
            throw new IllegalStateException(e);
        }
    }

}
