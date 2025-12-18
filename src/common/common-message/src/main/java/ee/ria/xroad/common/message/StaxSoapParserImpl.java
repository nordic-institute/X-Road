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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.identifier.XRoadObjectType;
import ee.ria.xroad.common.util.HeaderValueUtils;
import ee.ria.xroad.common.util.MimeUtils;

import com.ctc.wstx.stax.WstxInputFactory;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.xml.soap.SOAPException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.io.input.TeeInputStream;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.message.SoapUtils.validateMimeType;
import static ee.ria.xroad.common.util.HeaderValueUtils.hasUtf8Charset;
import static ee.ria.xroad.common.util.MimeUtils.UTF8;
import static org.niis.xroad.common.core.exception.ErrorCode.DUPLICATE_HEADER_FIELD;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_BODY;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_SOAP;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_XML;
import static org.niis.xroad.common.core.exception.ErrorCode.MISSING_BODY;
import static org.niis.xroad.common.core.exception.ErrorCode.MISSING_HEADER;
import static org.niis.xroad.common.core.exception.ErrorCode.MISSING_HEADER_FIELD;

/**
 * StAX-based SOAP message parser using Woodstox.
 * This implementation uses pull-based parsing for better performance and cleaner code flow.
 */
@Slf4j
public class StaxSoapParserImpl implements SoapParser {

    private static final String URI_IDENTIFIERS = "http://x-road.eu/xsd/identifiers";
    private static final String URI_REPRESENTATION = "http://x-road.eu/xsd/representation.xsd";
    private static final String URI_ENVELOPE = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String URI_ENCODING = "http://schemas.xmlsoap.org/soap/encoding/";

    private static final String ENVELOPE = "Envelope";
    private static final String HEADER = "Header";
    private static final String BODY = "Body";
    private static final String FAULT = "Fault";

    private static final String FAULT_CODE = "faultcode";
    private static final String FAULT_STRING = "faultstring";
    private static final String FAULT_ACTOR = "faultactor";
    private static final String FAULT_DETAIL = "detail";

    private static final String QUERY_ID = "id";
    private static final String USER_ID = "userId";
    private static final String ISSUE = "issue";
    private static final String REPRESENTED_PARTY = "representedParty";
    private static final String PARTY_CLASS = "partyClass";
    private static final String PARTY_CODE = "partyCode";
    private static final String PROTOCOL_VERSION = "protocolVersion";
    private static final String CLIENT = "client";
    private static final String SERVICE = "service";
    private static final String SECURITY_SERVER = "securityServer";
    private static final String REQUEST_HASH = "requestHash";
    private static final String INSTANCE = "xRoadInstance";
    private static final String MEMBER_CLASS = "memberClass";
    private static final String MEMBER_CODE = "memberCode";
    private static final String SUBSYSTEM_CODE = "subsystemCode";
    private static final String SERVICE_CODE = "serviceCode";
    private static final String SERVICE_VERSION = "serviceVersion";
    private static final String SERVER_CODE = "serverCode";

    private static final String ATTR_OBJECT_TYPE = "objectType";
    private static final String ATTR_ALGORITHM_ID = "algorithmId";
    private static final String ATTR_ENCODING_STYLE = "encodingStyle";

    private static final QName QNAME_SOAP_ENVELOPE = new QName(SoapUtils.NS_SOAPENV, ENVELOPE);
    private static final QName QNAME_SOAP_HEADER = new QName(SoapUtils.NS_SOAPENV, HEADER);
    private static final QName QNAME_SOAP_BODY = new QName(SoapUtils.NS_SOAPENV, BODY);
    private static final QName QNAME_SOAP_FAULT = new QName(SoapUtils.NS_SOAPENV, FAULT);

    private static final QName QNAME_XROAD_QUERY_ID = new QName(SoapHeader.NS_XROAD, QUERY_ID);
    private static final QName QNAME_XROAD_USER_ID = new QName(SoapHeader.NS_XROAD, USER_ID);
    private static final QName QNAME_XROAD_ISSUE = new QName(SoapHeader.NS_XROAD, ISSUE);
    private static final QName QNAME_REPR_REPRESENTED_PARTY = new QName(SoapHeader.NS_REPR, REPRESENTED_PARTY);
    private static final QName QNAME_XROAD_PROTOCOL_VERSION = new QName(SoapHeader.NS_XROAD, PROTOCOL_VERSION);
    private static final QName QNAME_XROAD_REQUEST_HASH = new QName(SoapHeader.NS_XROAD, REQUEST_HASH);
    private static final QName QNAME_XROAD_CLIENT = new QName(SoapHeader.NS_XROAD, CLIENT);
    private static final QName QNAME_XROAD_SERVICE = new QName(SoapHeader.NS_XROAD, SERVICE);
    private static final QName QNAME_XROAD_SECURITY_SERVER = new QName(SoapHeader.NS_XROAD, SECURITY_SERVER);

    private static final String MISSING_HEADER_MESSAGE = "Malformed SOAP message: header missing";
    private static final String MISSING_SERVICE_MESSAGE = "Message header must contain service id";
    private static final String MISSING_HEADER_FIELD_MESSAGE = "Required field '%s' is missing";
    private static final String DUPLICATE_HEADER_MESSAGE = "SOAP header contains duplicate field '%s'";
    private static final String MISSING_BODY_MESSAGE = "Malformed SOAP message: body missing";
    private static final String INVALID_BODY_MESSAGE = "Malformed SOAP message: body must have exactly one child element";
    private static final String MISSING_ENVELOPE_MESSAGE = "Malformed SOAP message: envelope missing";

    private static final XMLInputFactory INPUT_FACTORY = createInputFactory();

    private static XMLInputFactory createInputFactory() {
        WstxInputFactory factory = new WstxInputFactory();
        // Security: disable DTD and external entities
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        // Performance settings
        factory.setProperty(XMLInputFactory.IS_COALESCING, false);
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        return factory;
    }

    @Override
    @WithSpan
    public Soap parse(String contentType, InputStream is) {
        String mimeType = MimeUtils.getBaseContentType(contentType);

        String charset = HeaderValueUtils.getCharset(contentType);
        charset = StringUtils.isNotBlank(charset) ? charset : UTF8;

        if (mimeType != null) {
            validateMimeType(mimeType);
        }

        try {
            return parseMessage(is, contentType, charset);
        } catch (Exception e) {
            throw translateException(e);
        }
    }

    private Soap parseMessage(InputStream is, String contentType, String charset)
            throws IOException, SOAPException {
        log.trace("parseMessage({}, {})", contentType, charset);

        ByteArrayOutputStream rawXml = new ByteArrayOutputStream();

        // Read and buffer the input
        InputStream proxyStream = excludeUtf8Bom(contentType, new TeeInputStream(is, rawXml));

        // Parse using StAX
        try (var reader = new AutoCloseableWrapper(INPUT_FACTORY.createXMLStreamReader(proxyStream, charset))) {

            ParseResult result = parseXml(reader.reader);

            if (result.fault != null) {
                return new SoapFault(
                        result.fault.faultCode,
                        result.fault.faultString,
                        result.fault.faultActor,
                        result.fault.faultDetail,
                        rawXml.toByteArray(),
                        charset);
            }

            return new SoapMessageImpl(
                    rawXml.toByteArray(),
                    charset,
                    result.header,
                    null,
                    result.serviceName,
                    result.isRpc,
                    contentType);
        } catch (XMLStreamException e) {
            throw new SOAPException(e);
        }
    }

    private ParseResult parseXml(XMLStreamReader reader) throws XMLStreamException {
        SoapHeader header = new SoapHeader();
        ParseResult result = new ParseResult();
        result.header = header;

        boolean foundEnvelope = false;
        boolean foundHeader = false;
        boolean foundBody = false;

        while (reader.hasNext()) {
            int event = reader.next();

            if (event == XMLStreamConstants.START_ELEMENT) {
                QName element = reader.getName();

                if (element.equals(QNAME_SOAP_ENVELOPE)) {
                    foundEnvelope = true;
                    result.isRpc = URI_ENCODING.equals(
                            reader.getAttributeValue(URI_ENVELOPE, ATTR_ENCODING_STYLE));
                } else if (element.equals(QNAME_SOAP_HEADER)) {
                    foundHeader = true;
                    parseHeader(reader, header);
                } else if (element.equals(QNAME_SOAP_BODY)) {
                    foundBody = true;
                    BodyParseResult bodyResult = parseBody(reader);
                    result.serviceName = bodyResult.serviceName;
                    result.fault = bodyResult.fault;
                }
            }
        }

        // Validation
        if (!foundEnvelope) {
            throw XrdRuntimeException.systemException(INVALID_SOAP, MISSING_ENVELOPE_MESSAGE);
        }

        if (result.fault == null) {
            if (!foundHeader) {
                throw XrdRuntimeException.systemException(MISSING_HEADER, MISSING_HEADER_MESSAGE);
            }
            if (!foundBody) {
                throw XrdRuntimeException.systemException(MISSING_BODY, MISSING_BODY_MESSAGE);
            }

            validateHeader(header);

            if (result.serviceName == null) {
                throw XrdRuntimeException.systemException(INVALID_BODY, INVALID_BODY_MESSAGE);
            }

            SoapUtils.validateServiceName(header.getService().getServiceCode(), result.serviceName);
        }

        return result;
    }

    private void parseHeader(XMLStreamReader reader, SoapHeader header) throws XMLStreamException {
        while (reader.hasNext()) {
            int event = reader.next();

            if (event == XMLStreamConstants.START_ELEMENT) {
                QName element = reader.getName();

                if (element.equals(QNAME_XROAD_QUERY_ID)) {
                    validateDuplicate(element, header.getQueryId());
                    header.setQueryId(reader.getElementText());
                } else if (element.equals(QNAME_XROAD_USER_ID)) {
                    validateDuplicate(element, header.getUserId());
                    header.setUserId(reader.getElementText());
                } else if (element.equals(QNAME_XROAD_ISSUE)) {
                    validateDuplicate(element, header.getIssue());
                    header.setIssue(reader.getElementText());
                } else if (element.equals(QNAME_XROAD_PROTOCOL_VERSION)) {
                    validateDuplicate(element, header.getProtocolVersion());
                    header.setProtocolVersion(new ProtocolVersion(reader.getElementText()));
                } else if (element.equals(QNAME_XROAD_CLIENT)) {
                    validateDuplicate(element, header.getClient());
                    header.setClient(parseClientId(reader));
                } else if (element.equals(QNAME_XROAD_SERVICE)) {
                    validateDuplicate(element, header.getService());
                    header.setService(parseServiceId(reader));
                } else if (element.equals(QNAME_REPR_REPRESENTED_PARTY)) {
                    validateDuplicate(element, header.getRepresentedParty());
                    header.setRepresentedParty(parseRepresentedParty(reader));
                } else if (element.equals(QNAME_XROAD_SECURITY_SERVER)) {
                    validateDuplicate(element, header.getSecurityServer());
                    header.setSecurityServer(parseSecurityServerId(reader));
                } else if (element.equals(QNAME_XROAD_REQUEST_HASH)) {
                    validateDuplicate(element, header.getRequestHash());
                    header.setRequestHash(parseRequestHash(reader));
                } else {
                    skipElement(reader);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (reader.getName().equals(QNAME_SOAP_HEADER)) {
                    return;
                }
            }
        }
    }

    private ClientId.Conf parseClientId(XMLStreamReader reader) throws XMLStreamException {
        validateObjectType(reader, XRoadObjectType.MEMBER, XRoadObjectType.SUBSYSTEM);

        String instance = null;
        String memberClass = null;
        String memberCode = null;
        String subsystemCode = null;

        while (reader.hasNext()) {
            int event = reader.next();

            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();
                String namespace = reader.getNamespaceURI();

                if (URI_IDENTIFIERS.equals(namespace)) {
                    switch (localName) {
                        case INSTANCE -> instance = reader.getElementText();
                        case MEMBER_CLASS -> memberClass = reader.getElementText();
                        case MEMBER_CODE -> memberCode = reader.getElementText();
                        case SUBSYSTEM_CODE -> subsystemCode = reader.getElementText();
                        default -> skipElement(reader);
                    }
                } else {
                    skipElement(reader);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (reader.getName().equals(QNAME_XROAD_CLIENT)) {
                    break;
                }
            }
        }

        return ClientId.Conf.create(instance, memberClass, memberCode, subsystemCode);
    }

    private ServiceId.Conf parseServiceId(XMLStreamReader reader) throws XMLStreamException {
        validateObjectType(reader, XRoadObjectType.SERVICE);

        String instance = null;
        String memberClass = null;
        String memberCode = null;
        String subsystemCode = null;
        String serviceCode = null;
        String serviceVersion = null;

        while (reader.hasNext()) {
            int event = reader.next();

            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();
                String namespace = reader.getNamespaceURI();

                if (URI_IDENTIFIERS.equals(namespace)) {
                    switch (localName) {
                        case INSTANCE -> instance = reader.getElementText();
                        case MEMBER_CLASS -> memberClass = reader.getElementText();
                        case MEMBER_CODE -> memberCode = reader.getElementText();
                        case SUBSYSTEM_CODE -> subsystemCode = reader.getElementText();
                        case SERVICE_CODE -> serviceCode = reader.getElementText();
                        case SERVICE_VERSION -> serviceVersion = reader.getElementText();
                        default -> skipElement(reader);
                    }
                } else {
                    skipElement(reader);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (reader.getName().equals(QNAME_XROAD_SERVICE)) {
                    break;
                }
            }
        }

        return ServiceId.Conf.create(instance, memberClass, memberCode, subsystemCode, serviceCode, serviceVersion);
    }

    private SecurityServerId.Conf parseSecurityServerId(XMLStreamReader reader) throws XMLStreamException {
        validateObjectType(reader, XRoadObjectType.SERVER);

        String instance = null;
        String memberClass = null;
        String memberCode = null;
        String serverCode = null;

        while (reader.hasNext()) {
            int event = reader.next();

            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();
                String namespace = reader.getNamespaceURI();

                if (URI_IDENTIFIERS.equals(namespace)) {
                    switch (localName) {
                        case INSTANCE -> instance = reader.getElementText();
                        case MEMBER_CLASS -> memberClass = reader.getElementText();
                        case MEMBER_CODE -> memberCode = reader.getElementText();
                        case SERVER_CODE -> serverCode = reader.getElementText();
                        default -> skipElement(reader);
                    }
                } else {
                    skipElement(reader);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (reader.getName().equals(QNAME_XROAD_SECURITY_SERVER)) {
                    break;
                }
            }
        }

        return SecurityServerId.Conf.create(instance, memberClass, memberCode, serverCode);
    }

    private RepresentedParty parseRepresentedParty(XMLStreamReader reader) throws XMLStreamException {
        String partyClass = null;
        String partyCode = null;

        while (reader.hasNext()) {
            int event = reader.next();

            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();
                String namespace = reader.getNamespaceURI();

                if (URI_REPRESENTATION.equals(namespace)) {
                    switch (localName) {
                        case PARTY_CLASS -> partyClass = reader.getElementText();
                        case PARTY_CODE -> partyCode = reader.getElementText();
                        default -> skipElement(reader);
                    }
                } else {
                    skipElement(reader);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (reader.getName().equals(QNAME_REPR_REPRESENTED_PARTY)) {
                    break;
                }
            }
        }

        return new RepresentedParty(partyClass, partyCode);
    }

    private RequestHash parseRequestHash(XMLStreamReader reader) throws XMLStreamException {
        String algorithmId = reader.getAttributeValue(null, ATTR_ALGORITHM_ID);
        String hash = reader.getElementText();
        return new RequestHash(algorithmId, hash);
    }

    private BodyParseResult parseBody(XMLStreamReader reader) throws XMLStreamException {
        BodyParseResult result = new BodyParseResult();
        int childElementCount = 0;

        while (reader.hasNext()) {
            int event = reader.next();

            if (event == XMLStreamConstants.START_ELEMENT) {
                QName element = reader.getName();

                if (element.equals(QNAME_SOAP_FAULT)) {
                    result.fault = parseFault(reader);
                    return result;
                } else {
                    childElementCount++;
                    if (childElementCount == 1) {
                        result.serviceName = element.getLocalPart();
                    } else {
                        throw XrdRuntimeException.systemException(INVALID_BODY, INVALID_BODY_MESSAGE);
                    }
                    skipElement(reader);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (reader.getName().equals(QNAME_SOAP_BODY)) {
                    return result;
                }
            }
        }

        return result;
    }

    private FaultData parseFault(XMLStreamReader reader) throws XMLStreamException {
        FaultData fault = new FaultData();

        while (reader.hasNext()) {
            int event = reader.next();

            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();

                switch (localName) {
                    case FAULT_CODE -> fault.faultCode = reader.getElementText();
                    case FAULT_STRING -> fault.faultString = reader.getElementText();
                    case FAULT_ACTOR -> fault.faultActor = reader.getElementText();
                    case FAULT_DETAIL -> fault.faultDetail = parseFaultDetail(reader);
                    default -> skipElement(reader);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (reader.getName().equals(QNAME_SOAP_FAULT)) {
                    return fault;
                }
            }
        }

        return fault;
    }

    private String parseFaultDetail(XMLStreamReader reader) throws XMLStreamException {
        StringBuilder detail = new StringBuilder();

        while (reader.hasNext()) {
            int event = reader.next();

            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();
                if ("faultDetail".equals(localName)) {
                    return reader.getElementText();
                }
                skipElement(reader);
            } else if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                detail.append(reader.getText());
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (FAULT_DETAIL.equals(reader.getLocalName())) {
                    String text = detail.toString().trim();
                    return text.isEmpty() ? null : text;
                }
            }
        }

        return null;
    }

    private void validateObjectType(XMLStreamReader reader, XRoadObjectType... expected) {
        String objectType = reader.getAttributeValue(URI_IDENTIFIERS, ATTR_OBJECT_TYPE);

        if (objectType == null) {
            throw XrdRuntimeException.systemException(INVALID_XML, "Missing objectType attribute");
        }

        XRoadObjectType type;
        try {
            type = XRoadObjectType.valueOf(objectType);
        } catch (IllegalArgumentException e) {
            throw XrdRuntimeException.systemException(INVALID_XML, "Unknown objectType: %s".formatted(objectType));
        }

        for (XRoadObjectType exp : expected) {
            if (exp == type) {
                return;
            }
        }

        throw XrdRuntimeException.systemException(INVALID_XML, "Unexpected objectType: %s".formatted(objectType));
    }

    private void validateHeader(SoapHeader header) {
        if (header.getProtocolVersion() == null) {
            onMissingRequiredField(PROTOCOL_VERSION);
        }
        if (header.getClient() == null) {
            onMissingRequiredField(CLIENT);
        }
        if (header.getQueryId() == null) {
            onMissingRequiredField(QUERY_ID);
        }
        if (header.getService() == null) {
            throw XrdRuntimeException.systemException(MISSING_HEADER_FIELD, MISSING_SERVICE_MESSAGE);
        }
    }

    private void onMissingRequiredField(String fieldName) {
        throw XrdRuntimeException.systemException(MISSING_HEADER_FIELD, MISSING_HEADER_FIELD_MESSAGE.formatted(fieldName));
    }

    private void validateDuplicate(QName element, Object existing) {
        if (existing != null) {
            throw XrdRuntimeException.systemException(DUPLICATE_HEADER_FIELD,
                    DUPLICATE_HEADER_MESSAGE.formatted(element));
        }
    }

    private void skipElement(XMLStreamReader reader) throws XMLStreamException {
        int depth = 1;
        while (reader.hasNext() && depth > 0) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
    }

    private InputStream excludeUtf8Bom(String contentType, InputStream soapStream) throws IOException {
        return hasUtf8Charset(contentType) ? BOMInputStream.builder().setInputStream(soapStream).get() : soapStream;
    }

    private static final class ParseResult {
        SoapHeader header;
        String serviceName;
        boolean isRpc;
        FaultData fault;
    }

    private static final class BodyParseResult {
        String serviceName;
        FaultData fault;
    }

    private static final class FaultData {
        String faultCode;
        String faultString;
        String faultActor;
        String faultDetail;
    }


    private record AutoCloseableWrapper(XMLStreamReader reader) implements AutoCloseable {

        @Override
        public void close() throws XMLStreamException {
            reader.close();
        }
    }
}

