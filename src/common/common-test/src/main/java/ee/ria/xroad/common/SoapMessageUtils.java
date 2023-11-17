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
package ee.ria.xroad.common;

import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapUtils;

import jakarta.xml.soap.Detail;
import jakarta.xml.soap.SOAPBody;
import jakarta.xml.soap.SOAPElement;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPFault;
import jakarta.xml.soap.SOAPMessage;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.List;
import java.util.Map;

/**
 * Contains various utility methods for working with SOAP messages.
 */
public final class SoapMessageUtils {

    private SoapMessageUtils() {
    }

    /**
     * Retrieves the value of an child of a given parent with the specified name.
     * @param e the parent element
     * @param tagName name of the element
     * @return String value of the element
     */
    public static String getElementValue(Element e, String tagName) {
        NodeList list = e.getElementsByTagName(tagName);
        if (list.getLength() > 0) {
            return list.item(0).getTextContent();
        }

        throw new CodedException(ErrorCodes.X_INVALID_SOAP,
                "Could not find element '%s'", tagName);
    }

    /**
     * Creates a new child element under the specified parent with the given
     * name and value.
     * @param parent parent of the new element
     * @param name name of the new element
     * @param value value of the new element
     * @throws Exception in case of any errors
     */
    public static void createElement(SOAPElement parent, String name,
            String value) throws Exception {
        parent.addChildElement(name).setTextContent(value);
    }

    /**
     * Converts the given request SOAP message to a response SOAP message.
     * @param requestMessage the message to be converted
     * @return the original message with the request element swapped for a response
     * element in it's body
     * @throws Exception in case of any errors
     */
    public static SoapMessageImpl toResponse(SoapMessageImpl requestMessage)
            throws Exception {
        SoapMessageImpl responseMessage = SoapUtils.toResponse(requestMessage);

        List<SOAPElement> children = SoapUtils.getChildElements(
                responseMessage.getSoap().getSOAPBody());
        if (children.isEmpty()) {
            throw new CodedException(ErrorCodes.X_INVALID_SOAP,
                    "Missing response element");
        }

        SOAPElement responseElement = children.get(0);
        responseElement.removeContents(); // clear contents of response element

        return responseMessage;
    }

    /**
     * Retrieves the response element from the given message.
     * @param responseMessage the response message
     * @return the response SOAPElement
     * @throws Exception if the SOAP message did not contain a response element
     */
    public static SOAPElement getResponseElement(SoapMessageImpl responseMessage)
            throws Exception {
        SOAPBody responseBody = responseMessage.getSoap().getSOAPBody();

        List<SOAPElement> children = SoapUtils.getChildElements(responseBody);
        if (children.isEmpty()) {
            throw new CodedException(ErrorCodes.X_INVALID_SOAP,
                    "Missing response element");
        }

        return children.get(0);
    }

    /**
     * Converts the given map of request data to an XML String.
     * @param requestData map of key-value pairs
     * @return String
     */
    public static String getMessageContent(Map<String, String> requestData) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : requestData.entrySet()) {
            String fieldName = entry.getKey();
            String fieldRow = String.format("<%s>%s</%s>", fieldName,
                    entry.getValue(), fieldName);
            sb.append(fieldRow);
        }
        return sb.toString();
    }

    /**
     * @param message the SOAP message
     * @return true if the SOAP message is a fault response
     * @throws SOAPException if the SOAP Body does not exist or cannot be retrieved
     */
    public static boolean isFaultResponse(SOAPMessage message)
            throws SOAPException {
        return message.getSOAPBody().getFault() != null;
    }

    /**
     * Retrieves the fault code, string and detail from a SOAP fault message.
     * @param message the SOAP message
     * @return a String array containing the fault code, string and detail or
     * null if the message is not a SOAP fault
     * @throws SOAPException if the SOAP Body does not exist or cannot be retrieved
     */
    public static String[] getFaultCodeAndString(SOAPMessage message)
            throws SOAPException {
        SOAPFault fault = message.getSOAPBody().getFault();
        if (fault == null) {
            return null;
        }

        return new String[] {fault.getFaultCode(), fault.getFaultString(),
                detail(fault.getDetail())};
    }

    static String detail(Detail detail) {
        if (detail != null && detail.getFirstChild() != null) {
            return detail.getFirstChild().getTextContent();
        }

        return "";
    }
}
