package ee.cyber.sdsb.common.message;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import javax.xml.soap.SOAPFault;
import javax.xml.soap.SOAPMessage;
import javax.xml.transform.dom.DOMSource;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.util.MimeUtils;

import static ee.cyber.sdsb.common.ErrorCodes.*;
import static ee.cyber.sdsb.common.message.SoapUtils.*;
import static ee.cyber.sdsb.common.util.MimeUtils.UTF8;
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

        byte[] data = IOUtils.toByteArray(is);
        InputStream buffered = new ByteArrayInputStream(data);

        // Explicitly check content type to produce better error code
        // for client.
        if (mimeType != null) {
            validateMimeType(mimeType);
        }

        SOAPMessage soap = createSOAPMessage(buffered, charset);
        return parseMessage(data, soap, charset);
    }

    protected Soap parseMessage(byte[] data, SOAPMessage soap, String charset)
            throws Exception {
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

        return createMessage(data, soap, charset);
    }

    protected Soap createMessage(byte[] data, SOAPMessage soap, String charset)
            throws Exception {
        // Request and response messages must have a header,
        // fault messages may or may not have a header.
        SoapHeader header = soap.getSOAPHeader() != null
                ? new SoapHeader(charset, soap.getSOAPHeader()) : null;

        if (header == null) {
            throw new CodedException(X_MISSING_HEADER,
                    "Malformed SOAP message: header missing");
        }

        String serviceName = getServiceName(soap.getSOAPBody());
        validateServiceName(header.service.getServiceCode(), serviceName);

        String xml = new String(data, charset);
        return new SoapMessageImpl(xml, soap, header, serviceName);
    }

    protected void validateAgainstSoapSchema(SOAPMessage soap)
            throws Exception {
        SoapSchemaValidator.validate(
                new DOMSource(soap.getSOAPPart().getDocumentElement()));
    }

}
