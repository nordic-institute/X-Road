package ee.cyber.sdsb.common.message;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.soap.*;

import ee.cyber.sdsb.common.CodedException;

import static ee.cyber.sdsb.common.ErrorCodes.*;
import static ee.cyber.sdsb.common.util.MimeUtils.contentTypeWithCharset;
import static org.eclipse.jetty.http.MimeTypes.TEXT_XML;

public class SoapUtils {

    public static final String RPC_ATTR = "SOAP-ENV:encodingStyle";
    public static final String RPC_ENCODING =
            "http://schemas.xmlsoap.org/soap/encoding/";

    private static final String SOAP_SUFFIX_RESPONSE = "Response";

    /**
     * Returns true, if the SOAP message is RPC-encoded.
     */
    public static boolean isRpcMessage(SOAPMessage soap) throws Exception {
        SOAPEnvelope envelope = soap.getSOAPPart().getEnvelope();
        return RPC_ENCODING.equals(envelope.getAttribute(RPC_ATTR));
    }

    /**
     * Checks whether the service name denotes it is a response message.
     */
    public static boolean isResponseMessage(String serviceName) {
        return serviceName.endsWith(SOAP_SUFFIX_RESPONSE);
    }

    /**
     * Checks that the service name matches in header and body.
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
     * Checks request and response consistency.
     */
    public static boolean checkConsistency(SoapMessageImpl request,
            SoapMessageImpl response) {
        return SoapHeader.checkConsistency(request.header, response.header);
    }

    /**
     * Converts a SOAP request to SOAP response, by adding "Response" suffix
     * to the service name in the body.
     */
    public static SoapMessageImpl toResponse(SoapMessageImpl request)
            throws Exception {
        String charset = request.getCharset();

        SOAPMessage soap = createSOAPMessage(
                new ByteArrayInputStream(request.getBytes()), charset);

        List<SOAPElement> children = getChildElements(soap.getSOAPBody());
        if (children.size() == 0) {
            return null;
        }

        QName name = children.get(0).getElementQName();
        QName newName = new QName(name.getNamespaceURI(),
                name.getLocalPart() + SOAP_SUFFIX_RESPONSE,
                name.getPrefix());
        children.get(0).setElementQName(newName);

        byte[] data = getBytes(soap, charset);
        return (SoapMessageImpl) new SoapParserImpl().parseMessage(data, soap,
                charset);
    }

    /**
     * Returns the XML representing the SOAP message.
     */
    public static String getXml(SoapMessageImpl message) throws IOException {
        return getXml(message.soap, message.getCharset());
    }

    /**
     * Returns the XML representing the SOAP message.
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
     * Returns the XML representing the SOAP message.
     */
    public static byte[] getBytes(SOAPMessage soap, String charset)
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

        return out.toByteArray();
    }

    /**
     * Returns all children that are of the SOAPElement
     * type of the given element.
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
     */
    public static void validateMimeType(String mimeType) {
        if (!TEXT_XML.equalsIgnoreCase(mimeType)) {
            throw new CodedException(X_INVALID_CONTENT_TYPE,
                    "Invalid content type: %s", mimeType);
        }
    }

    /**
     * Creates a new SOAPMessage object.
     */
    public static SOAPMessage createSOAPMessage(InputStream is, String charset)
            throws SOAPException, IOException {
        MimeHeaders mimeHeaders = new MimeHeaders();
        mimeHeaders.addHeader("Content-type",
                contentTypeWithCharset(TEXT_XML, charset));

        MessageFactory factory = MessageFactory.newInstance();
        return factory.createMessage(mimeHeaders, is);
    }

    /**
     * Returns a map of SOAP header elements. Header field name is the key.
     */
    public static Map<String, SOAPElement> getHeaderElements(
            SOAPHeader header) {
        Map<String, SOAPElement> values = new HashMap<>();

        Iterator<?> it = header.getChildElements();
        while (it.hasNext()) {
            Object child = it.next();
            if (child instanceof SOAPElement) {
                SOAPElement element = (SOAPElement) child;
                String name = element.getLocalName();
                if (values.containsKey(name)) {
                    throw new CodedException(X_DUPLICATE_HEADER_FIELD,
                            "Duplicate header field: %s", name);
                }

                values.put(name, element);
            }
        }

        return values;
    }

    /**
     * Returns the SOAPElement from the given header element map. If the
     * element does not exist, throws a CodedException with error code
     * X_MISSING_HEADER_FIELD.
     */
    public static SOAPElement getRequiredHeaderElement(
            Map<String, SOAPElement> header, String fieldName) {
        SOAPElement element = header.get(fieldName);
        checkRequiredField(fieldName, element);
        return element;
    }

    /**
     * Returns the value of the SOAPElement of the header. If the
     * element does not exist, throws a CodedException with error code
     * X_MISSING_HEADER_FIELD.
     */
    public static String getRequiredHeaderValue(
            Map<String, SOAPElement> header, String fieldName) {
        return getRequiredHeaderElement(header, fieldName).getValue();
    }

    /**
     * Returns the value of the SOAPElement of the header or empty string,
     * if the header does not contain that element.
     */
    public static String getOptionalHeaderValue(
            Map<String, SOAPElement> header, String fieldName) {
        return header.get(fieldName) != null
                ? header.get(fieldName).getValue() : null;
    }

    /**
     * Checks if the header field has a value. Throws a CodedException
     * with error code X_MISSING_HEADER_FIELD, if the value is null or empty
     * string.
     */
    public static void checkRequiredField(String fieldName, Object value) {
        if (value == null ||
                (value instanceof String && ((String) value).isEmpty())) {
            throw new CodedException(X_MISSING_HEADER_FIELD,
                    "Missing required header field: %s", fieldName);
        }
    }
}
