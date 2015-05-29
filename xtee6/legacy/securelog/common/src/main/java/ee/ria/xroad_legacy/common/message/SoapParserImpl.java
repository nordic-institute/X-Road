package ee.ria.xroad_legacy.common.message;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPFault;
import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPMessage;
import javax.xml.transform.dom.DOMSource;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.ria.xroad_legacy.common.CodedException;
import ee.ria.xroad_legacy.common.util.MimeUtils;

import static ee.ria.xroad_legacy.common.ErrorCodes.*;
import static ee.ria.xroad_legacy.common.message.SoapUtils.*;
import static ee.ria.xroad_legacy.common.util.MimeUtils.UTF8;
import static org.eclipse.jetty.http.MimeTypes.TEXT_XML;


public class SoapParserImpl implements SoapParser {

    private static final Logger LOG =
            LoggerFactory.getLogger(SoapParserImpl.class);

    @Override
    public Soap parse(String mimeType, String charset, InputStream is) {
        try {
            return parseMessage(mimeType, charset, is);
        } catch (Exception e) {
            throw translateException(e);
        }
    }

    public Soap parse(InputStream is) {
        return parse(TEXT_XML, UTF8, is);
    }

    public Soap parse(String contentType, InputStream is) {
        String mimeType = MimeUtils.getBaseContentType(contentType);
        String charset = MimeUtils.getCharset(contentType);
        return parse(mimeType, charset, is);
    }

    protected Soap parseMessage(String mimeType, String charset,
            InputStream is) throws Exception {
        LOG.debug("parseMessage({}, {})", mimeType, charset);

        if (charset == null) {
            charset = UTF8;
        }

        // We need to keep the original XML around for various logging reasons.
        byte[] rawXml = IOUtils.toByteArray(is);

        // Explicitly check content type to produce better error code
        // for client.
        if (mimeType != null) {
            validateMimeType(mimeType);
        }

        SOAPMessage soap =
                createSOAPMessage(new ByteArrayInputStream(rawXml), charset);
        return parseMessage(rawXml, soap, charset);
    }

    protected Soap parseMessage(byte[] rawXml, SOAPMessage soap,
            String charset) throws Exception {
        if (soap.getSOAPBody() == null) {
            throw new CodedException(X_MISSING_BODY,
                    "Malformed SOAP message: body missing");
        }

        // Currently only validate D/L wrapped messages, since there is an
        // error in SOAP Schema that prohibits the use of encodingStyle
        // attribute in the SOAP envelope.
        if (!SoapUtils.isRpcMessage(soap)) {
            validateAgainstSoapSchema(soap);
        }

        SOAPFault fault = soap.getSOAPBody().getFault();
        if (fault != null) {
            return new SoapFault(fault);
        }

        return createMessage(rawXml, soap, charset);
    }

    protected Soap createMessage(byte[] rawXml, SOAPMessage soap,
            String charset) throws Exception {
        // Request and response messages must have a header,
        // fault messages may or may not have a header.
        SoapHeader header = null;
        if (soap.getSOAPHeader() != null) {
            validateSOAPHeader(soap.getSOAPHeader());
            header = unmarshalHeader(SoapHeader.class, soap.getSOAPHeader());
        }

        return createMessage(rawXml, header, soap, charset);
    }

    protected Soap createMessage(byte[] rawXml, SoapHeader header,
            SOAPMessage soap, String charset) throws Exception {
        if (header == null) {
            throw new CodedException(X_MISSING_HEADER,
                    "Malformed SOAP message: header missing");
        }

        String serviceName = getServiceName(soap.getSOAPBody());
        validateServiceName(header.getService().getServiceCode(), serviceName);

        String xml = new String(rawXml, charset);
        return new SoapMessageImpl(xml, charset, header, soap, serviceName);
    }

    protected void validateAgainstSoapSchema(SOAPMessage soap)
            throws Exception {
        SoapSchemaValidator.validate(
                new DOMSource(soap.getSOAPPart().getDocumentElement()));
    }

    public static void validateSOAPHeader(SOAPHeader soapHeader) {
        // Check for duplicate fields
        Set<QName> fields = new HashSet<>();
        Iterator<?> it = soapHeader.getChildElements();
        while (it.hasNext()) {
            Object next = it.next();
            if (next instanceof SOAPElement) {
                SOAPElement soapElement = (SOAPElement) next;
                if (!fields.add(soapElement.getElementQName())) {
                    throw new CodedException(X_DUPLICATE_HEADER_FIELD,
                            "SOAP header contains duplicate field '%s'",
                            soapElement.getElementQName());
                }
            }
        }
    }

    public static <T> T unmarshalHeader(Class<?> clazz, SOAPHeader soapHeader)
            throws Exception {
        return unmarshalHeader(clazz, soapHeader, true);
    }

    @SuppressWarnings("unchecked")
    public static <T> T unmarshalHeader(Class<?> clazz, SOAPHeader soapHeader,
            boolean checkRequiredFields) throws Exception {
        Unmarshaller unmarshaller = JaxbUtils.createUnmarshaller(clazz);

        if (checkRequiredFields) {
            unmarshaller.setListener(new RequiredHeaderFieldsChecker(clazz));
        }

        JAXBElement<T> jaxbElement =
                (JAXBElement<T>) unmarshaller.unmarshal(soapHeader, clazz);
        return jaxbElement.getValue();
    }
}

