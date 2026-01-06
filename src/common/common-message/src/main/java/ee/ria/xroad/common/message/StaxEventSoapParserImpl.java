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

import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.xml.soap.SOAPException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.io.input.TeeInputStream;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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
 * StAX-based SOAP message parser using XMLEventReader.
 * This implementation uses the event-based API where each call to nextEvent()
 * returns an XMLEvent object that can be inspected, stored, or passed around.
 */
@Slf4j
public class StaxEventSoapParserImpl implements SoapParser {

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
    protected static final String ATTR_ALGORITHM_ID = "algorithmId";
    private static final String ATTR_ENCODING_STYLE = "encodingStyle";

    private static final QName QNAME_SOAP_ENVELOPE = new QName(SoapUtils.NS_SOAPENV, ENVELOPE);
    protected static final QName QNAME_SOAP_HEADER = new QName(SoapUtils.NS_SOAPENV, HEADER);
    private static final QName QNAME_SOAP_BODY = new QName(SoapUtils.NS_SOAPENV, BODY);
    private static final QName QNAME_SOAP_FAULT = new QName(SoapUtils.NS_SOAPENV, FAULT);

    protected static final QName QNAME_XROAD_QUERY_ID = new QName(SoapHeader.NS_XROAD, QUERY_ID);
    private static final QName QNAME_XROAD_USER_ID = new QName(SoapHeader.NS_XROAD, USER_ID);
    private static final QName QNAME_XROAD_ISSUE = new QName(SoapHeader.NS_XROAD, ISSUE);
    private static final QName QNAME_REPR_REPRESENTED_PARTY = new QName(SoapHeader.NS_REPR, REPRESENTED_PARTY);
    private static final QName QNAME_XROAD_PROTOCOL_VERSION = new QName(SoapHeader.NS_XROAD, PROTOCOL_VERSION);
    protected static final QName QNAME_XROAD_REQUEST_HASH = new QName(SoapHeader.NS_XROAD, REQUEST_HASH);
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
        var factory = XMLInputFactory.newFactory();
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

    protected void onNextEvent(XMLEvent currentEvent, XMLEvent previousEvent) throws XMLStreamException {
        // noop
    }

    protected void afterDocument() throws XMLStreamException {
        // noop
    }

    protected InputStream prepareInputStream(InputStream rawInputStream, OutputStream rawOutputStream) throws XMLStreamException {
        return new TeeInputStream(rawInputStream, rawOutputStream);
    }

    private Soap parseMessage(InputStream is, String contentType, String charset)
            throws IOException, SOAPException {
        log.trace("parseMessage({}, {})", contentType, charset);

        // Parse using StAX XMLEventReader
        try (var rawXml = new ByteArrayOutputStream();
             var proxyStream = excludeUtf8Bom(contentType, prepareInputStream(is, rawXml));
             var reader = new EventReaderWrapper(INPUT_FACTORY.createXMLEventReader(proxyStream, charset))) {

            ParseResult result = parseXml(reader);

            if (result.fault != null) {
                return new SoapFault(
                        result.fault.faultCode,
                        result.fault.faultString,
                        result.fault.faultActor,
                        result.fault.faultDetail,
                        rawXml.toByteArray(),
                        charset);
            }
            afterDocument();

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

    private ParseResult parseXml(EventReaderWrapper reader) throws XMLStreamException {
        SoapHeader header = new SoapHeader();
        ParseResult result = new ParseResult();
        result.header = header;

        boolean foundEnvelope = false;
        boolean foundHeader = false;
        boolean foundBody = false;

        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();

            if (event.isStartElement()) {
                StartElement startElement = event.asStartElement();
                QName element = startElement.getName();

                if (element.equals(QNAME_SOAP_ENVELOPE)) {
                    foundEnvelope = true;
                    Attribute encodingStyle = startElement.getAttributeByName(
                            new QName(URI_ENVELOPE, ATTR_ENCODING_STYLE));
                    result.isRpc = encodingStyle != null && URI_ENCODING.equals(encodingStyle.getValue());
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

        return validateParseResult(result, foundEnvelope, foundHeader, foundBody);
    }

    private ParseResult validateParseResult(ParseResult result, boolean foundEnvelope, boolean foundHeader, boolean foundBody) {
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

            validateHeader(result.header);

            if (result.serviceName == null) {
                throw XrdRuntimeException.systemException(INVALID_BODY, INVALID_BODY_MESSAGE);
            }

            SoapUtils.validateServiceName(result.header.getService().getServiceCode(), result.serviceName);
        }

        return result;
    }

    private void parseHeader(EventReaderWrapper reader, SoapHeader header) throws XMLStreamException {
        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();

            if (event.isStartElement()) {
                parseHeaderComponents(reader, header, event.asStartElement());
            } else if (event.isEndElement()) {
                if (event.asEndElement().getName().equals(QNAME_SOAP_HEADER)) {
                    return;
                }
            }
        }
    }

    private void parseHeaderComponents(EventReaderWrapper reader, SoapHeader header, StartElement startElement)
            throws XMLStreamException {
        QName element = startElement.getName();

        if (element.equals(QNAME_XROAD_QUERY_ID)) {
            validateDuplicate(element, header.getQueryId());
            header.setQueryId(readElementText(reader, element));
        } else if (element.equals(QNAME_XROAD_USER_ID)) {
            validateDuplicate(element, header.getUserId());
            header.setUserId(readElementText(reader, element));
        } else if (element.equals(QNAME_XROAD_ISSUE)) {
            validateDuplicate(element, header.getIssue());
            header.setIssue(readElementText(reader, element));
        } else if (element.equals(QNAME_XROAD_PROTOCOL_VERSION)) {
            validateDuplicate(element, header.getProtocolVersion());
            header.setProtocolVersion(new ProtocolVersion(readElementText(reader, element)));
        } else if (element.equals(QNAME_XROAD_CLIENT)) {
            validateDuplicate(element, header.getClient());
            header.setClient(parseClientId(reader, startElement));
        } else if (element.equals(QNAME_XROAD_SERVICE)) {
            validateDuplicate(element, header.getService());
            header.setService(parseServiceId(reader, startElement));
        } else if (element.equals(QNAME_REPR_REPRESENTED_PARTY)) {
            validateDuplicate(element, header.getRepresentedParty());
            header.setRepresentedParty(parseRepresentedParty(reader));
        } else if (element.equals(QNAME_XROAD_SECURITY_SERVER)) {
            validateDuplicate(element, header.getSecurityServer());
            header.setSecurityServer(parseSecurityServerId(reader, startElement));
        } else if (element.equals(QNAME_XROAD_REQUEST_HASH)) {
            validateDuplicate(element, header.getRequestHash());
            header.setRequestHash(parseRequestHash(reader, startElement));
        } else {
            skipElement(reader);
        }
    }

    private ClientId.Conf parseClientId(EventReaderWrapper reader, StartElement clientElement) throws XMLStreamException {
        validateObjectType(clientElement, XRoadObjectType.MEMBER, XRoadObjectType.SUBSYSTEM);

        String instance = null;
        String memberClass = null;
        String memberCode = null;
        String subsystemCode = null;

        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();

            if (event.isStartElement()) {
                StartElement startElement = event.asStartElement();
                String localName = startElement.getName().getLocalPart();
                String namespace = startElement.getName().getNamespaceURI();

                if (URI_IDENTIFIERS.equals(namespace)) {
                    switch (localName) {
                        case INSTANCE -> instance = readElementText(reader, startElement.getName());
                        case MEMBER_CLASS -> memberClass = readElementText(reader, startElement.getName());
                        case MEMBER_CODE -> memberCode = readElementText(reader, startElement.getName());
                        case SUBSYSTEM_CODE -> subsystemCode = readElementText(reader, startElement.getName());
                        default -> skipElement(reader);
                    }
                } else {
                    skipElement(reader);
                }
            } else if (event.isEndElement()) {
                if (event.asEndElement().getName().equals(QNAME_XROAD_CLIENT)) {
                    break;
                }
            }
        }

        return ClientId.Conf.create(instance, memberClass, memberCode, subsystemCode);
    }

    private ServiceId.Conf parseServiceId(EventReaderWrapper reader, StartElement serviceElement) throws XMLStreamException {
        validateObjectType(serviceElement, XRoadObjectType.SERVICE);

        String instance = null;
        String memberClass = null;
        String memberCode = null;
        String subsystemCode = null;
        String serviceCode = null;
        String serviceVersion = null;

        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();

            if (event.isStartElement()) {
                StartElement startElement = event.asStartElement();
                String localName = startElement.getName().getLocalPart();
                String namespace = startElement.getName().getNamespaceURI();

                if (URI_IDENTIFIERS.equals(namespace)) {
                    switch (localName) {
                        case INSTANCE -> instance = readElementText(reader, startElement.getName());
                        case MEMBER_CLASS -> memberClass = readElementText(reader, startElement.getName());
                        case MEMBER_CODE -> memberCode = readElementText(reader, startElement.getName());
                        case SUBSYSTEM_CODE -> subsystemCode = readElementText(reader, startElement.getName());
                        case SERVICE_CODE -> serviceCode = readElementText(reader, startElement.getName());
                        case SERVICE_VERSION -> serviceVersion = readElementText(reader, startElement.getName());
                        default -> skipElement(reader);
                    }
                } else {
                    skipElement(reader);
                }
            } else if (event.isEndElement()) {
                if (event.asEndElement().getName().equals(QNAME_XROAD_SERVICE)) {
                    break;
                }
            }
        }

        return ServiceId.Conf.create(instance, memberClass, memberCode, subsystemCode, serviceCode, serviceVersion);
    }

    private SecurityServerId.Conf parseSecurityServerId(EventReaderWrapper reader, StartElement serverElement)
            throws XMLStreamException {
        validateObjectType(serverElement, XRoadObjectType.SERVER);

        String instance = null;
        String memberClass = null;
        String memberCode = null;
        String serverCode = null;

        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();

            if (event.isStartElement()) {
                StartElement startElement = event.asStartElement();
                String localName = startElement.getName().getLocalPart();
                String namespace = startElement.getName().getNamespaceURI();

                if (URI_IDENTIFIERS.equals(namespace)) {
                    switch (localName) {
                        case INSTANCE -> instance = readElementText(reader, startElement.getName());
                        case MEMBER_CLASS -> memberClass = readElementText(reader, startElement.getName());
                        case MEMBER_CODE -> memberCode = readElementText(reader, startElement.getName());
                        case SERVER_CODE -> serverCode = readElementText(reader, startElement.getName());
                        default -> skipElement(reader);
                    }
                } else {
                    skipElement(reader);
                }
            } else if (event.isEndElement()) {
                if (event.asEndElement().getName().equals(QNAME_XROAD_SECURITY_SERVER)) {
                    break;
                }
            }
        }

        return SecurityServerId.Conf.create(instance, memberClass, memberCode, serverCode);
    }

    private RepresentedParty parseRepresentedParty(EventReaderWrapper reader) throws XMLStreamException {
        String partyClass = null;
        String partyCode = null;

        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();

            if (event.isStartElement()) {
                StartElement startElement = event.asStartElement();
                String localName = startElement.getName().getLocalPart();
                String namespace = startElement.getName().getNamespaceURI();

                if (URI_REPRESENTATION.equals(namespace)) {
                    switch (localName) {
                        case PARTY_CLASS -> partyClass = readElementText(reader, startElement.getName());
                        case PARTY_CODE -> partyCode = readElementText(reader, startElement.getName());
                        default -> skipElement(reader);
                    }
                } else {
                    skipElement(reader);
                }
            } else if (event.isEndElement()) {
                if (event.asEndElement().getName().equals(QNAME_REPR_REPRESENTED_PARTY)) {
                    break;
                }
            }
        }

        return new RepresentedParty(partyClass, partyCode);
    }

    private RequestHash parseRequestHash(EventReaderWrapper reader, StartElement startElement) throws XMLStreamException {
        Attribute algorithmAttr = startElement.getAttributeByName(new QName(ATTR_ALGORITHM_ID));
        String algorithmId = algorithmAttr != null ? algorithmAttr.getValue() : null;
        String hash = readElementText(reader, startElement.getName());
        return new RequestHash(algorithmId, hash);
    }

    private BodyParseResult parseBody(EventReaderWrapper reader) throws XMLStreamException {
        BodyParseResult result = new BodyParseResult();
        int childElementCount = 0;

        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();

            if (event.isStartElement()) {
                StartElement startElement = event.asStartElement();
                QName element = startElement.getName();

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
            } else if (event.isEndElement()) {
                if (event.asEndElement().getName().equals(QNAME_SOAP_BODY)) {
                    return result;
                }
            }
        }

        return result;
    }

    private FaultData parseFault(EventReaderWrapper reader) throws XMLStreamException {
        FaultData fault = new FaultData();

        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();

            if (event.isStartElement()) {
                StartElement startElement = event.asStartElement();
                String localName = startElement.getName().getLocalPart();

                switch (localName) {
                    case FAULT_CODE -> fault.faultCode = readElementText(reader, startElement.getName());
                    case FAULT_STRING -> fault.faultString = readElementText(reader, startElement.getName());
                    case FAULT_ACTOR -> fault.faultActor = readElementText(reader, startElement.getName());
                    case FAULT_DETAIL -> fault.faultDetail = parseFaultDetail(reader);
                    default -> skipElement(reader);
                }
            } else if (event.isEndElement()) {
                if (event.asEndElement().getName().equals(QNAME_SOAP_FAULT)) {
                    return fault;
                }
            }
        }

        return fault;
    }

    private String parseFaultDetail(EventReaderWrapper reader) throws XMLStreamException {
        StringBuilder detail = new StringBuilder();

        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();

            if (event.isStartElement()) {
                StartElement startElement = event.asStartElement();
                String localName = startElement.getName().getLocalPart();
                if ("faultDetail".equals(localName)) {
                    return readElementText(reader, startElement.getName());
                }
                skipElement(reader);
            } else if (event.isCharacters()) {
                Characters characters = event.asCharacters();
                detail.append(characters.getData());
            } else if (event.isEndElement()) {
                EndElement endElement = event.asEndElement();
                if (FAULT_DETAIL.equals(endElement.getName().getLocalPart())) {
                    String text = detail.toString().trim();
                    return text.isEmpty() ? null : text;
                }
            }
        }

        return null;
    }

    /**
     * Reads the text content of the current element until its end tag.
     * Similar to XMLStreamReader.getElementText() but for XMLEventReader.
     */
    private String readElementText(EventReaderWrapper reader, QName elementName) throws XMLStreamException {
        StringBuilder text = new StringBuilder();

        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();

            if (event.isCharacters()) {
                text.append(event.asCharacters().getData());
            } else if (event.isEndElement()) {
                if (event.asEndElement().getName().equals(elementName)) {
                    return text.toString();
                }
            } else if (event.isStartElement()) {
                // Nested element - this shouldn't happen for simple text elements
                // but we'll skip it to be safe
                skipElement(reader);
            }
        }

        return text.toString();
    }

    private void validateObjectType(StartElement startElement, XRoadObjectType... expected) {
        Attribute objectTypeAttr = startElement.getAttributeByName(new QName(URI_IDENTIFIERS, ATTR_OBJECT_TYPE));

        if (objectTypeAttr == null) {
            throw XrdRuntimeException.systemException(INVALID_XML, "Missing objectType attribute");
        }

        String objectType = objectTypeAttr.getValue();
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

    private void skipElement(EventReaderWrapper reader) throws XMLStreamException {
        int depth = 1;
        while (reader.hasNext() && depth > 0) {
            XMLEvent event = reader.nextEvent();
            if (event.isStartElement()) {
                depth++;
            } else if (event.isEndElement()) {
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

    @RequiredArgsConstructor
    protected final class EventReaderWrapper implements AutoCloseable {

        private final XMLEventReader reader;
        private XMLEvent currentEvent;

        public XMLEvent nextEvent() throws XMLStreamException {
            var previousEvent = currentEvent;
            currentEvent = reader.nextEvent();
            onNextEvent(currentEvent, previousEvent);
            return currentEvent;
        }

        public boolean hasNext() {
            return reader.hasNext();
        }

        @Override
        public void close() throws XMLStreamException {
            reader.close();
        }
    }
}

