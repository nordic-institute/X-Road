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
package ee.ria.xroad.common.message;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.namespace.QName;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;

import org.apache.commons.lang.ObjectUtils;

import ee.ria.xroad.common.CodedException;

import static ee.ria.xroad.common.ErrorCodes.*;
import static ee.ria.xroad.common.util.MimeUtils.contentTypeWithCharset;
import static org.eclipse.jetty.http.MimeTypes.TEXT_XML;

/**
 * Contains utility methods for working with SOAP messages.
 */
public final class SoapUtils {

    // HTTP header field for async messages that are sent from async-sender
    public static final String X_IGNORE_ASYNC = "X-Ignore-Async";

    public static final String PREFIX_SOAPENV = "SOAP-ENV";

    public static final String RPC_ATTR = PREFIX_SOAPENV + ":encodingStyle";

    public static final String RPC_ENCODING =
            "http://schemas.xmlsoap.org/soap/encoding/";

    public static final String NS_SOAPENV =
            "http://schemas.xmlsoap.org/soap/envelope/";

    public static final MessageFactory MESSAGE_FACTORY = initMessageFactory();

    private static final String SOAP_SUFFIX_RESPONSE = "Response";

    /**
     * Functional interface for the callback executed on newly created SOAP message objects.
     */
    public interface SOAPCallback {
        /**
         * Called on newly created SOAP message objects.
         * @param soap newly created SOAP message object
         * @throws Exception if any errors occur
         */
        void call(SOAPMessage soap) throws Exception;
    }

    private SoapUtils() {
    }

    private static MessageFactory initMessageFactory() {
        try {
            return MessageFactory.newInstance();
        } catch (SOAPException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns all namespace prefixes of a given SOAP element.
     * @param element the SOAP element from which to retrieve namespace prefixes
     * @return a List of namespace prefix Strings
     */
    public static List<String> getNamespacePrefixes(SOAPElement element) {
        List<String> nsPrefixes = new ArrayList<>();

        Iterator<?> it = element.getNamespacePrefixes();
        while (it.hasNext()) {
            nsPrefixes.add(it.next().toString());
        }

        return nsPrefixes;
    }

    /**
     * Returns namespace URIs from a SOAPMessage.
     * @param soap soap message from which to retrieve namespace URIs
     * @return a List of namespace URI Strings
     * @throws Exception if there is a SOAP error
     */
    public static List<String> getNamespaceURIs(SOAPMessage soap)
            throws Exception {
        List<String> nsURIs = new ArrayList<>();

        SOAPEnvelope envelope = soap.getSOAPPart().getEnvelope();
        Iterator<?> it = envelope.getNamespacePrefixes();
        while (it.hasNext()) {
            nsURIs.add(envelope.getNamespaceURI((String) it.next()));
        }

        return nsURIs;
    }

    /**
     * Returns the XmlElement annotation for the given field, if the annotation
     * exists. Returns null, if field has no such annotation.
     * @param field the field from which to get the XmlElement annotation
     * @return the XmlElement annotation
     */
    public static XmlElement getXmlElementAnnotation(Field field) {
        for (Annotation annotation : field.getDeclaredAnnotations()) {
            if (annotation instanceof XmlElement) {
                return (XmlElement) annotation;
            }
        }

        return null;
    }

    /**
     * Returns true, if the SOAP message is RPC-encoded.
     * @param soap the SOAP message
     * @return boolean
     * @throws Exception if there are errors reading the SOAP envelope
     */
    public static boolean isRpcMessage(SOAPMessage soap) throws Exception {
        SOAPEnvelope envelope = soap.getSOAPPart().getEnvelope();
        return RPC_ENCODING.equals(envelope.getAttribute(RPC_ATTR));
    }

    /**
     * Checks whether the service name denotes it is a response message.
     * @param serviceName service name inside the SOAP message body
     * @return boolean
     */
    public static boolean isResponseMessage(String serviceName) {
        return serviceName.endsWith(SOAP_SUFFIX_RESPONSE);
    }

    /**
     * Checks that the service name matches in header and body.
     * @param serviceCode service code inside the SOAP message header
     * @param serviceName service name inside the SOAP message body
     */
    public static void validateServiceName(String serviceCode,
            String serviceName) {
        if (!serviceName.startsWith(serviceCode)) {
            throw new CodedException(X_INCONSISTENT_HEADERS,
                    "Malformed SOAP message: "
                        + "service code does not match in header and body");
        }
    }

    /**
     * Returns the service name from the soap body.
     * @param soapBody body of the SOAP message
     * @return a String containing the service name
     */
    public static String getServiceName(SOAPBody soapBody) {
        List<SOAPElement> children = getChildElements(soapBody);
        if (children.size() != 1) {
            throw new CodedException(X_INVALID_BODY,
                    "Malformed SOAP message: "
                            + "body must have exactly one child element");
        }

        return children.get(0).getLocalName();
    }

    /**
     * Checks consistency of soap headers of two SOAP messages.
     * @param m1 the first SOAP message
     * @param m2 the second SOAP message
     */
    public static void checkConsistency(SoapMessageImpl m1,
            SoapMessageImpl m2) {
        checkConsistency(m1.getHeader(), m2.getHeader());
    }

    /**
     * Checks consistency of two SOAP headers.
     * @param h1 the first SOAP header
     * @param h2 the second SOAP header
     */
    public static void checkConsistency(SoapHeader h1, SoapHeader h2) {
        for (Field field : SoapHeader.class.getDeclaredFields()) {
            if (field.isAnnotationPresent(CheckConsistency.class)) {
                Object value1 = getFieldValue(field, h1);
                Object value2 = getFieldValue(field, h2);
                if (ObjectUtils.notEqual(value1, value2)) {
                    throw new CodedException(X_INCONSISTENT_HEADERS,
                            "Field '%s' does not match in request and response",
                            field.getName());
                }
            }
        }
    }

    /**
     * Converts a SOAP request to SOAP response, by adding "Response" suffix
     * to the service name in the body.
     * @param request SOAP request message to be converted
     * @return the response SoapMessageImpl
     * @throws Exception if errors occur during response message creation
     */
    public static SoapMessageImpl toResponse(SoapMessageImpl request)
            throws Exception {
        return toResponse(request, null);
    }

    /**
     * Converts a SOAP request to SOAP response, by adding "Response" suffix
     * to the service name in the body.
     * @param request SOAP request message to be converted
     * @param callback function to call when the response SOAP object has been created
     * @return the response SoapMessageImpl
     * @throws Exception if errors occur during response message creation
     */
    public static SoapMessageImpl toResponse(SoapMessageImpl request,
            SOAPCallback callback) throws Exception {
        String charset = request.getCharset();

        SOAPMessage soap = createSOAPMessage(new ByteArrayInputStream(
                        request.getBytes()), charset);

        List<SOAPElement> bodyChildren = getChildElements(soap.getSOAPBody());
        if (bodyChildren.size() == 0) {
            return null;
        }

        QName requestElementQName = bodyChildren.get(0).getElementQName();
        String serviceCode = getServiceCode(soap, requestElementQName);

        QName newName = new QName(requestElementQName.getNamespaceURI(),
                serviceCode + SOAP_SUFFIX_RESPONSE,
                requestElementQName.getPrefix());
        bodyChildren.get(0).setElementQName(newName);

        if (callback != null) {
            callback.call(soap);
        }

        byte[] xml = getBytes(soap);
        return (SoapMessageImpl) new SoapParserImpl().parseMessage(xml, soap,
                charset);
    }

    private static String getServiceCode(
            SOAPMessage soap, QName requestElementQName) throws SOAPException {
        for (SOAPElement eachHeaderElement
                : getChildElements(soap.getSOAPHeader())) {
            QName headerElementQName = eachHeaderElement.getElementQName();

            if (!"service".equals(headerElementQName.getLocalPart())) {
                continue;
            }

            for (SOAPElement eachServicePart
                    : getChildElements(eachHeaderElement)) {
                QName headerPartQName = eachServicePart.getElementQName();

                if (headerPartQName.getLocalPart().equals("serviceCode")) {
                    return eachServicePart.getValue();
                }
            }
        }

        return requestElementQName.getLocalPart();
    }

    /**
     * Returns the XML representing the SOAP message.
     * @param message message to be converted into XML
     * @return XML String representing the soap message
     * @throws IOException in case of errors
     */
    public static String getXml(SoapMessageImpl message) throws IOException {
        try {
            return message.getXml();
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    /**
     * Returns the XML representing the SOAP message.
     * @param soap message to be converted into XML
     * @param charset charset to use when generating the XML string
     * @return XML String representing the soap message
     * @throws IOException in case of errors
     */
    public static String getXml(SOAPMessage soap, String charset)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            soap.setProperty(SOAPMessage.WRITE_XML_DECLARATION, "true");
            soap.writeTo(out);
        } catch (SOAPException e) {
            // Avoid throwing SOAPException, since it is essentially an
            // IOException if we cannot produce the XML
            throw new IOException(e);
        }

        return out.toString(charset);
    }

    /**
     * Returns the XML representing the SOAP message as bytes.
     * @param soap message to be converted to byte content
     * @return byte[]
     * @throws IOException if errors occur while writing message bytes
     */
    public static byte[] getBytes(SOAPMessage soap) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            soap.setProperty(SOAPMessage.WRITE_XML_DECLARATION, "true");
            soap.writeTo(out);
        } catch (SOAPException e) {
            // Avoid throwing SOAPException, since it is essentially an
            // IOException if we cannot produce the XML
            throw new IOException(e);
        }

        return out.toByteArray();
    }

    /**
     * Returns all children that are of the SOAPElement
     * type of the given element.
     * @param element parent for which to retrieve the children
     * @return List of SOAPElements that can be found
     */
    public static List<SOAPElement> getChildElements(SOAPElement element) {
        List<SOAPElement> elements = new ArrayList<>();
        Iterator<?> it = element.getChildElements();
        while (it.hasNext()) {
            Object o = it.next();
            if (o instanceof SOAPElement) {
                elements.add((SOAPElement) o);
            }
        }
        return elements;
    }

    /**
     * Returns the first child that are of the SOAPElement
     * type of the given element.
     * @param element parent for which to retrieve the children
     * @return SOAPElement that is the first child of the given parent or null
     */
    public static SOAPElement getFirstChild(SOAPElement element) {
        Iterator<?> it = element.getChildElements();
        while (it.hasNext()) {
            Object o = it.next();
            if (o instanceof SOAPElement) {
                return (SOAPElement) o;
            }
        }

        return null;
    }

    /**
     * If the given mime type is not text/xml, throws an error.
     * @param mimeType the mimeType that's expected to be text/xml
     */
    public static void validateMimeType(String mimeType) {
        if (!TEXT_XML.equalsIgnoreCase(mimeType)) {
            throw new CodedException(X_INVALID_CONTENT_TYPE,
                    "Invalid content type: %s", mimeType);
        }
    }

    /**
     * Creates a new SOAPMessage object.
     * @param is input stream containing the SOAP object data
     * @param charset the expected charset of the input stream
     * @return SOAPMessage that's been read from the input stream
     * @throws SOAPException may be thrown if the message is invalid
     * @throws IOException if there is a problem in reading data from the input stream
     */
    public static SOAPMessage createSOAPMessage(InputStream is, String charset)
            throws SOAPException, IOException {
        MimeHeaders mimeHeaders = new MimeHeaders();
        mimeHeaders.addHeader("Content-type",
                contentTypeWithCharset(TEXT_XML, charset));

        return MESSAGE_FACTORY.createMessage(mimeHeaders, is);
    }

    static Object getFieldValue(Field field, Object object) {
        field.setAccessible(true);
        try {
            return field.get(object);
        } catch (Exception e) {
            throw translateException(e);
        }
    }

}
