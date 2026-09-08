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
import ee.ria.xroad.common.message.SoapUtils;
import ee.ria.xroad.common.message.StaxEventSoapParserImpl;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.proxy.core.protocol.ProxyMessage;

import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Optional;

import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.util.EncoderUtils.encodeBase64;

@RequiredArgsConstructor
final class ResponseStaxSoapParserImpl extends StaxEventSoapParserImpl {

    private static final XMLOutputFactory OUTPUT_FACTORY = XMLOutputFactory.newDefaultFactory();
    private static final XMLEventFactory EVENT_FACTORY = XMLEventFactory.newDefaultFactory();

    private final ProxyMessage requestMessage;
    private DelayedEventWriter writer;
    private boolean inHeader;
    private boolean inRequestHash = false;
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
        if (QNAME_SOAP_HEADER.equals(startElement.getName())) {
            inHeader = true;
        } else if (inHeader && QNAME_XROAD_QUERY_ID.equals(startElement.getName())) {
            headerWhiteSpace = writer.peekLast()
                    .filter(XMLEvent::isCharacters)
                    .map(XMLEvent::asCharacters)
                    .filter(Characters::isWhiteSpace)
                    .orElse(null);
        } else if (inHeader && QNAME_XROAD_REQUEST_HASH.equals(startElement.getName())) {
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
