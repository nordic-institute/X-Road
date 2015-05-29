package ee.ria.xroad_legacy.common.message;

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

import ee.ria.xroad_legacy.common.CodedException;

import static ee.ria.xroad_legacy.common.ErrorCodes.*;
import static ee.ria.xroad_legacy.common.util.MimeUtils.contentTypeWithCharset;
import static org.eclipse.jetty.http.MimeTypes.TEXT_XML;

public class SoapUtils {

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
     * Returns all namespace prefixes of a gievn SOAP element.
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

    public static void checkConsistency(SoapMessageImpl m1,
            SoapMessageImpl m2) {
        checkConsistency(m1.getHeader(), m2.getHeader());
    }

    /**
     * Checks consistency of two soap headers.
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
     */
    public static SoapMessageImpl toResponse(SoapMessageImpl request)
            throws Exception {
        String charset = request.getCharset();

        SOAPMessage soap = createSOAPMessage(new ByteArrayInputStream(
                        getBytes(request.getSoap(), charset)), charset);

        List<SOAPElement> children = getChildElements(soap.getSOAPBody());
        if (children.size() == 0) {
            return null;
        }

        QName name = children.get(0).getElementQName();
        QName newName = new QName(name.getNamespaceURI(),
                name.getLocalPart() + SOAP_SUFFIX_RESPONSE, name.getPrefix());
        children.get(0).setElementQName(newName);

        byte[] xml = getBytes(soap, charset);
        return (SoapMessageImpl) new SoapParserImpl().parseMessage(xml, soap,
                charset);
    }

    /**
     * Returns the XML representing the SOAP message.
     */
    public static String getXml(SoapMessageImpl message) throws IOException {
        return getXml(message.getSoap(), message.getCharset());
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
