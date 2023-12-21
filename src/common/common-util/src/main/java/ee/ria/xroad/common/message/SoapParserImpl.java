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
package ee.ria.xroad.common.message;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.util.MimeUtils;

import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.ValidationEvent;
import jakarta.xml.soap.SOAPElement;
import jakarta.xml.soap.SOAPFault;
import jakarta.xml.soap.SOAPHeader;
import jakarta.xml.soap.SOAPMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang3.StringUtils;
import org.glassfish.jaxb.runtime.api.AccessorException;

import javax.xml.namespace.QName;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static ee.ria.xroad.common.ErrorCodes.X_DUPLICATE_HEADER_FIELD;
import static ee.ria.xroad.common.ErrorCodes.X_MISSING_BODY;
import static ee.ria.xroad.common.ErrorCodes.X_MISSING_HEADER;
import static ee.ria.xroad.common.ErrorCodes.X_MISSING_HEADER_FIELD;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.message.SoapUtils.createSOAPMessage;
import static ee.ria.xroad.common.message.SoapUtils.getServiceName;
import static ee.ria.xroad.common.message.SoapUtils.isRpcMessage;
import static ee.ria.xroad.common.message.SoapUtils.validateMimeType;
import static ee.ria.xroad.common.message.SoapUtils.validateServiceName;
import static ee.ria.xroad.common.util.MimeUtils.UTF8;
import static ee.ria.xroad.common.util.MimeUtils.hasUtf8Charset;

/**
 * Default Soap parser implementation for reading Soap messages from an
 * input stream.
 */
@Slf4j
public class SoapParserImpl implements SoapParser {

    @Override
    public Soap parse(String contentType, InputStream is) {
        try {
            return parseMessage(contentType, is);
        } catch (Exception e) {
            throw translateException(e);
        }
    }

    protected Soap parseMessage(String contentType, InputStream is)
            throws Exception {
        String mimeType = MimeUtils.getBaseContentType(contentType);

        String charset = MimeUtils.getCharset(contentType);
        charset = StringUtils.isNotBlank(charset) ? charset : UTF8;

        log.trace("parseMessage({}, {})", mimeType, charset);

        // We need to keep the original XML around for various logging reasons.
        byte[] rawXml = IOUtils.toByteArray(is);

        // Explicitly check content type to produce better error code
        // for client.
        if (mimeType != null) {
            validateMimeType(mimeType);
        }

        // Detect and exclude a UTF-8 BOM.
        SOAPMessage soap = createSOAPMessage(excludeUtf8Bom(contentType,
                new ByteArrayInputStream(rawXml)), charset);

        return parseMessage(rawXml, soap, charset, contentType);
    }

    private InputStream excludeUtf8Bom(String contentType,
            InputStream soapStream) {
        return hasUtf8Charset(contentType)
                ? new BOMInputStream(soapStream) : soapStream;
    }

    protected Soap parseMessage(byte[] rawXml, SOAPMessage soap,
            String charset, String originalContentType) throws Exception {
        if (soap.getSOAPBody() == null) {
            throw new CodedException(X_MISSING_BODY,
                    "Malformed SOAP message: body missing");
        }

        SOAPFault fault = soap.getSOAPBody().getFault();
        if (fault != null) {
            return new SoapFault(fault, rawXml, charset);
        } else {
            return createMessage(rawXml, soap, charset, originalContentType);
        }
    }

    protected Soap createMessage(byte[] rawXml, SOAPMessage soap,
            String charset, String originalContentType) throws Exception {
        // Request and response messages must have a header,
        // fault messages may or may not have a header.
        SoapHeader header = null;
        if (soap.getSOAPHeader() != null) {
            validateSOAPHeader(soap.getSOAPHeader());
            header = unmarshalHeader(SoapHeader.class, soap.getSOAPHeader());
        }

        return createMessage(rawXml, header, soap, charset,
                originalContentType);
    }

    protected Soap createMessage(byte[] rawXml, SoapHeader header,
            SOAPMessage soap, String charset, String originalContentType)
                    throws Exception {
        if (header == null) {
            throw new CodedException(X_MISSING_HEADER,
                    "Malformed SOAP message: header missing");
        }

        String serviceName = getServiceName(soap.getSOAPBody());
        ServiceId service = header.getService();
        if (service == null) {
            throw new CodedException(X_MISSING_HEADER_FIELD,
                    "Message header must contain service id");
        }

        validateServiceName(service.getServiceCode(), serviceName);

        return new SoapMessageImpl(rawXml, charset, header, soap,
                serviceName, isRpcMessage(soap), originalContentType);
    }

    /**
     * Checks SOAP header for duplicate fields.
     * @param soapHeader the SOAP header
     */
    public static void validateSOAPHeader(SOAPHeader soapHeader) {
        // Check for duplicate fields
        Set<QName> fields = new HashSet<>();
        Iterator<?> it = soapHeader.getChildElements();
        while (it.hasNext()) {
            Object next = it.next();
            if (next instanceof SOAPElement soapElement) {
                if (!fields.add(soapElement.getElementQName())) {
                    throw new CodedException(X_DUPLICATE_HEADER_FIELD,
                            "SOAP header contains duplicate field '%s'",
                            soapElement.getElementQName());
                }
            }
        }
    }

    /**
     * Decodes header field values from the SOAP header into an instance of the
     * given class.
     * @param <T> the result type
     * @param clazz the header class
     * @param soapHeader the SOAP header
     * @return instance of T
     * @throws Exception in case of any errors
     */
    public static <T> T unmarshalHeader(Class<?> clazz, SOAPHeader soapHeader)
            throws Exception {
        return unmarshalHeader(clazz, soapHeader, true);
    }

    @SuppressWarnings("unchecked")
    static <T> T unmarshalHeader(Class<?> clazz, SOAPHeader soapHeader,
            boolean checkRequiredFields) throws Exception {
        Unmarshaller unmarshaller = JaxbUtils.createUnmarshaller(clazz);

        if (checkRequiredFields) {
            unmarshaller.setListener(new RequiredHeaderFieldsChecker(clazz));
        }

        unmarshaller.setEventHandler(event -> switch (event.getSeverity()) {
            case ValidationEvent.WARNING -> true;
            case ValidationEvent.ERROR -> {
                Throwable t = event.getLinkedException();
                yield !(t instanceof AccessorException
                        && t.getCause() instanceof CodedException);
            }
            case ValidationEvent.FATAL_ERROR -> false;
            default -> true;
        });

        JAXBElement<T> jaxbElement =
                (JAXBElement<T>) unmarshaller.unmarshal(soapHeader, clazz);
        return jaxbElement.getValue();
    }
}

