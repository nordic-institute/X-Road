/*
 * The MIT License
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
package org.niis.xroad.proxy.core.serverproxy;

import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.RepresentedParty;
import ee.ria.xroad.common.message.SoapHeader;
import ee.ria.xroad.common.message.SoapUtils;
import ee.ria.xroad.common.message.StaxEventSoapParserImpl;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.proxy.core.protocol.ProxyMessage;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.Namespace;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.util.EncoderUtils.encodeBase64;
import static org.niis.xroad.common.core.exception.ErrorCode.MISSING_HEADER_FIELD;

/**
 * Streaming SOAP parser for service responses on the server proxy.
 *
 * <p>In normal operation it copies the response verbatim and replaces the {@code xrd:requestHash}
 * with the hash of the originating request (the {@code xrd:id} element triggers the rewrite).
 *
 * <p>When auto-injection of missing headers is enabled and the response is a legacy SOAP message
 * without an X-Road {@code <Header>} (WCF / JAX-WS / ASMX style), the missing header is synthesized
 * from the originating request and written to the output stream immediately before the SOAP body,
 * so the processed response is validated, signed and returned like a normal X-Road response instead
 * of failing with {@code MISSING_HEADER}. The synthetic header follows the X-Road message protocol
 * field order (PR-MESS v4.0 §2.2): client, service, id, [userId], [issue], [representedParty],
 * protocolVersion, requestHash. The base parser stays neutral; all policy lives here.
 */
@RequiredArgsConstructor
final class ResponseStaxSoapParserImpl extends StaxEventSoapParserImpl {

    private static final XMLOutputFactory OUTPUT_FACTORY = XMLOutputFactory.newDefaultFactory();
    private static final XMLEventFactory EVENT_FACTORY = XMLEventFactory.newDefaultFactory();

    private static final String NS_IDENTIFIERS = "http://x-road.eu/xsd/identifiers";

    private static final String SYNTHETIC_PREFIX_XROAD = "xrd";
    private static final String SYNTHETIC_PREFIX_IDENTIFIERS = "id";
    private static final String SYNTHETIC_PREFIX_REPRESENTATION = "repr";

    private static final String ATTR_OBJECT_TYPE = "objectType";

    private static final String QUERY_ID = "id";
    private static final String USER_ID = "userId";
    private static final String ISSUE = "issue";
    private static final String PROTOCOL_VERSION = "protocolVersion";
    private static final String CLIENT = "client";
    private static final String SERVICE = "service";
    private static final String REPRESENTED_PARTY = "representedParty";
    private static final String PARTY_CLASS = "partyClass";
    private static final String PARTY_CODE = "partyCode";
    private static final String INSTANCE = "xRoadInstance";
    private static final String MEMBER_CLASS = "memberClass";
    private static final String MEMBER_CODE = "memberCode";
    private static final String SUBSYSTEM_CODE = "subsystemCode";
    private static final String SERVICE_CODE = "serviceCode";
    private static final String SERVICE_VERSION = "serviceVersion";

    // QName for the SOAP body (QNAME_SOAP_BODY is private in the base parser).
    private static final QName QNAME_SOAP_BODY = new QName(SoapUtils.NS_SOAPENV, "Body");

    // Indentation for the synthesized header (matches the indentation style of the existing fixtures).
    private static final String INDENT_ELEMENT = "\n    ";       // 4 spaces  (Header / Body level)
    private static final String INDENT_FIELD = "\n        ";     // 8 spaces  (header children)
    private static final String INDENT_PART = "\n            ";  // 12 spaces (identifier parts)

    private final ProxyMessage requestMessage;
    private final boolean autoInjectMissingHeaders;

    private DelayedEventWriter writer;
    private boolean inHeader;
    private boolean inRequestHash = false;
    private boolean headerSeen = false;
    private boolean missingHeaderInjected = false;
    private XMLEvent headerWhiteSpace = null;

    @Override
    protected InputStream prepareInputStream(InputStream rawInputStream, OutputStream rawOutputStream) throws XMLStreamException {
        writer = new DelayedEventWriter(OUTPUT_FACTORY.createXMLEventWriter(rawOutputStream));
        return rawInputStream;
    }

    @Override
    protected void afterDocument() throws XMLStreamException {
        writer.flush();
        writer.close();
    }

    /**
     * The base parser detects the missing header only after the whole stream has been consumed (the
     * synthetic header is already written to the output at the body start, see {@link #handleStartTag}).
     * Here we merely decide whether the absence is an error. With auto-injection disabled, behavior is
     * unchanged (the base rejects the message); with it enabled, the synthesized header makes the response
     * valid, so we suppress the rejection.
     */
    @Override
    protected void onMissingHeader() {
        if (!autoInjectMissingHeaders) {
            super.onMissingHeader();
        }
    }

    @Override
    protected void onNextEvent(XMLEvent currentEvent) throws XMLStreamException {
        if (writer.peekLast().filter(XMLEvent::isStartDocument).isPresent() && !currentEvent.isCharacters()) {
            //at least in some cases white space characters event is missing after start document <?xml ...?>
            writer.add(EVENT_FACTORY.createSpace("\n"));
        }

        if (currentEvent.isStartElement()) {
            handleStartTag(currentEvent.asStartElement());
        }
        if (!inRequestHash) {
            writer.add(currentEvent);
        }

        if (currentEvent.isEndElement()) {
            handleEndTag(currentEvent.asEndElement());
        }
    }

    private void handleStartTag(StartElement startElement) {
        QName name = startElement.getName();
        if (QNAME_SOAP_HEADER.equals(name)) {
            inHeader = true;
            headerSeen = true;
        } else if (QNAME_SOAP_BODY.equals(name)) {
            injectMissingHeaderIfNeeded(name.getPrefix());
        } else if (inHeader && QNAME_XROAD_QUERY_ID.equals(name)) {
            headerWhiteSpace = writer.peekLast()
                    .filter(XMLEvent::isCharacters)
                    .map(XMLEvent::asCharacters)
                    .filter(Characters::isWhiteSpace)
                    .orElse(null);
        } else if (inHeader && QNAME_XROAD_REQUEST_HASH.equals(name)) {
            inRequestHash = true;

            writer.peekLast()
                    .filter(XMLEvent::isCharacters)
                    .map(XMLEvent::asCharacters)
                    .filter(Characters::isWhiteSpace)
                    .ifPresent(evt -> writer.dropLast());
        }
    }

    private void handleEndTag(EndElement endElement) {
        if (QNAME_SOAP_HEADER.equals(endElement.getName())) {
            inHeader = false;
        } else if (inHeader && QNAME_XROAD_QUERY_ID.equals(endElement.getName())) {
            addRequestHash(endElement.getName().getPrefix());
        } else if (inHeader && QNAME_XROAD_REQUEST_HASH.equals(endElement.getName())) {
            inRequestHash = false;
        }
    }

    private void addRequestHash(String prefix) {
        try {
            byte[] hashBytes = requestMessage.getSoap().getHash();
            String hash = encodeBase64(hashBytes);

            DigestAlgorithm algoUri = SoapUtils.getHashAlgoId();

            if (headerWhiteSpace != null) {
                writer.add(headerWhiteSpace);
            }

            writer.add(EVENT_FACTORY.createStartElement(
                    prefix,
                    QNAME_XROAD_REQUEST_HASH.getNamespaceURI(),
                    QNAME_XROAD_REQUEST_HASH.getLocalPart()));
            writer.add(EVENT_FACTORY.createAttribute(ATTR_ALGORITHM_ID, algoUri.uri()));
            writer.add(EVENT_FACTORY.createCharacters(hash));
            writer.add(EVENT_FACTORY.createEndElement(
                    prefix,
                    QNAME_XROAD_REQUEST_HASH.getNamespaceURI(),
                    QNAME_XROAD_REQUEST_HASH.getLocalPart()));

            headerWhiteSpace = null;
        } catch (Exception e) {
            throw translateException(e);
        }
    }

    /**
     * Writes the synthetic X-Road SOAP header (reconstructed from the originating request) immediately
     * before the SOAP body, but only once and only when auto-injection is enabled and the response did
     * not contain a header of its own.
     */
    private void injectMissingHeaderIfNeeded(String soapPrefix) {
        if (headerSeen || missingHeaderInjected || !autoInjectMissingHeaders) {
            return;
        }
        try {
            writeSyntheticHeader(soapPrefix);
            missingHeaderInjected = true;
        } catch (Exception e) {
            throw translateException(e);
        }
    }

    private void writeSyntheticHeader(String soapPrefix) throws XMLStreamException {
        SoapHeader header = requestMessage.getSoap().getHeader();

        // The originating request header is normally fully populated and validated, but guard the
        // fields dereferenced below so a malformed request fails with a clear MISSING_HEADER_FIELD
        // fault rather than a NullPointerException while synthesizing the response header.
        requireRequestField(header.getClient(), CLIENT);
        requireRequestField(header.getService(), SERVICE);
        requireRequestField(header.getQueryId(), QUERY_ID);
        requireRequestField(header.getProtocolVersion(), PROTOCOL_VERSION);

        List<Namespace> namespaces = new ArrayList<>();
        namespaces.add(EVENT_FACTORY.createNamespace(SYNTHETIC_PREFIX_XROAD, SoapHeader.NS_XROAD));
        namespaces.add(EVENT_FACTORY.createNamespace(SYNTHETIC_PREFIX_IDENTIFIERS, NS_IDENTIFIERS));
        if (header.getRepresentedParty() != null) {
            namespaces.add(EVENT_FACTORY.createNamespace(SYNTHETIC_PREFIX_REPRESENTATION, SoapHeader.NS_REPR));
        }

        writer.add(EVENT_FACTORY.createStartElement(soapPrefix, SoapUtils.NS_SOAPENV, "Header",
                Collections.<Attribute>emptyIterator(), namespaces.iterator()));

        writeClientElement(header.getClient());
        writeServiceElement(header.getService());
        writeXRoadTextElement(QUERY_ID, header.getQueryId());
        if (header.getUserId() != null) {
            writeXRoadTextElement(USER_ID, header.getUserId());
        }
        if (header.getIssue() != null) {
            writeXRoadTextElement(ISSUE, header.getIssue());
        }
        if (header.getRepresentedParty() != null) {
            writeRepresentedPartyElement(header.getRepresentedParty());
        }
        writeXRoadTextElement(PROTOCOL_VERSION, header.getProtocolVersion().getVersion());
        writeSyntheticRequestHash();

        writer.add(EVENT_FACTORY.createCharacters(INDENT_ELEMENT));
        writer.add(EVENT_FACTORY.createEndElement(soapPrefix, SoapUtils.NS_SOAPENV, "Header"));
        writer.add(EVENT_FACTORY.createCharacters(INDENT_ELEMENT));
    }

    private static void requireRequestField(Object value, String fieldName) {
        if (value == null) {
            throw XrdRuntimeException.systemException(MISSING_HEADER_FIELD,
                    "Request header field '%s' is required to synthesize the response header".formatted(fieldName));
        }
    }

    private void writeClientElement(ClientId client) throws XMLStreamException {
        writer.add(EVENT_FACTORY.createCharacters(INDENT_FIELD));
        writer.add(EVENT_FACTORY.createStartElement(SYNTHETIC_PREFIX_XROAD, SoapHeader.NS_XROAD, CLIENT,
                objectTypeAttribute(client.getObjectType().name()), Collections.<Namespace>emptyIterator()));
        writeIdentifierPart(INSTANCE, client.getXRoadInstance());
        writeIdentifierPart(MEMBER_CLASS, client.getMemberClass());
        writeIdentifierPart(MEMBER_CODE, client.getMemberCode());
        if (client.getSubsystemCode() != null) {
            writeIdentifierPart(SUBSYSTEM_CODE, client.getSubsystemCode());
        }
        writer.add(EVENT_FACTORY.createCharacters(INDENT_FIELD));
        writer.add(EVENT_FACTORY.createEndElement(SYNTHETIC_PREFIX_XROAD, SoapHeader.NS_XROAD, CLIENT));
    }

    private void writeServiceElement(ServiceId service) throws XMLStreamException {
        writer.add(EVENT_FACTORY.createCharacters(INDENT_FIELD));
        writer.add(EVENT_FACTORY.createStartElement(SYNTHETIC_PREFIX_XROAD, SoapHeader.NS_XROAD, SERVICE,
                objectTypeAttribute(service.getObjectType().name()), Collections.<Namespace>emptyIterator()));
        writeIdentifierPart(INSTANCE, service.getXRoadInstance());
        writeIdentifierPart(MEMBER_CLASS, service.getMemberClass());
        writeIdentifierPart(MEMBER_CODE, service.getMemberCode());
        if (service.getSubsystemCode() != null) {
            writeIdentifierPart(SUBSYSTEM_CODE, service.getSubsystemCode());
        }
        writeIdentifierPart(SERVICE_CODE, service.getServiceCode());
        if (service.getServiceVersion() != null) {
            writeIdentifierPart(SERVICE_VERSION, service.getServiceVersion());
        }
        writer.add(EVENT_FACTORY.createCharacters(INDENT_FIELD));
        writer.add(EVENT_FACTORY.createEndElement(SYNTHETIC_PREFIX_XROAD, SoapHeader.NS_XROAD, SERVICE));
    }

    private void writeRepresentedPartyElement(RepresentedParty representedParty) throws XMLStreamException {
        writer.add(EVENT_FACTORY.createCharacters(INDENT_FIELD));
        writer.add(EVENT_FACTORY.createStartElement(SYNTHETIC_PREFIX_REPRESENTATION, SoapHeader.NS_REPR, REPRESENTED_PARTY));
        if (representedParty.getPartyClass() != null) {
            writeTextElement(INDENT_PART, SYNTHETIC_PREFIX_REPRESENTATION, SoapHeader.NS_REPR, PARTY_CLASS,
                    representedParty.getPartyClass());
        }
        writeTextElement(INDENT_PART, SYNTHETIC_PREFIX_REPRESENTATION, SoapHeader.NS_REPR, PARTY_CODE,
                representedParty.getPartyCode());
        writer.add(EVENT_FACTORY.createCharacters(INDENT_FIELD));
        writer.add(EVENT_FACTORY.createEndElement(SYNTHETIC_PREFIX_REPRESENTATION, SoapHeader.NS_REPR, REPRESENTED_PARTY));
    }

    private void writeSyntheticRequestHash() throws XMLStreamException {
        byte[] hashBytes = requestMessage.getSoap().getHash();
        String hash = encodeBase64(hashBytes);
        DigestAlgorithm algoUri = SoapUtils.getHashAlgoId();

        writer.add(EVENT_FACTORY.createCharacters(INDENT_FIELD));
        writer.add(EVENT_FACTORY.createStartElement(
                SYNTHETIC_PREFIX_XROAD,
                QNAME_XROAD_REQUEST_HASH.getNamespaceURI(),
                QNAME_XROAD_REQUEST_HASH.getLocalPart()));
        writer.add(EVENT_FACTORY.createAttribute(ATTR_ALGORITHM_ID, algoUri.uri()));
        writer.add(EVENT_FACTORY.createCharacters(hash));
        writer.add(EVENT_FACTORY.createEndElement(
                SYNTHETIC_PREFIX_XROAD,
                QNAME_XROAD_REQUEST_HASH.getNamespaceURI(),
                QNAME_XROAD_REQUEST_HASH.getLocalPart()));
    }

    private void writeIdentifierPart(String localName, String value) throws XMLStreamException {
        writeTextElement(INDENT_PART, SYNTHETIC_PREFIX_IDENTIFIERS, NS_IDENTIFIERS, localName, value);
    }

    private void writeXRoadTextElement(String localName, String value) throws XMLStreamException {
        writeTextElement(INDENT_FIELD, SYNTHETIC_PREFIX_XROAD, SoapHeader.NS_XROAD, localName, value);
    }

    private void writeTextElement(String indent, String prefix, String namespaceUri, String localName, String value)
            throws XMLStreamException {
        writer.add(EVENT_FACTORY.createCharacters(indent));
        writer.add(EVENT_FACTORY.createStartElement(prefix, namespaceUri, localName));
        writer.add(EVENT_FACTORY.createCharacters(value));
        writer.add(EVENT_FACTORY.createEndElement(prefix, namespaceUri, localName));
    }

    private java.util.Iterator<Attribute> objectTypeAttribute(String objectType) {
        return List.of(EVENT_FACTORY.createAttribute(
                SYNTHETIC_PREFIX_IDENTIFIERS, NS_IDENTIFIERS, ATTR_OBJECT_TYPE, objectType)).iterator();
    }

    /**
     * For keeping track of few last events for handling some edge cases.
     */
    @RequiredArgsConstructor
    private static final class DelayedEventWriter {
        private static final int MAX_LENGTH = 3;

        private final Deque<XMLEvent> queue = new LinkedList<>();
        private final XMLEventWriter delegate;

        public void add(XMLEvent event) throws XMLStreamException {
            while (queue.size() >= MAX_LENGTH) {
                delegate.add(queue.removeFirst());
            }
            queue.addLast(event);
        }

        public Optional<XMLEvent> peekLast() {
            return Optional.ofNullable(queue.peekLast());
        }

        public void dropLast() {
            queue.removeLast();
        }

        public void flush() throws XMLStreamException {
            for (var event : queue) {
                delegate.add(event);
            }
            delegate.flush();
        }

        public void close() throws XMLStreamException {
            delegate.close();
        }
    }
}
