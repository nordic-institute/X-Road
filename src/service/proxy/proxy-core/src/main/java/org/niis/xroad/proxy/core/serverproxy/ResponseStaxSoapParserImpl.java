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
package org.niis.xroad.proxy.core.serverproxy;

import ee.ria.xroad.common.message.SoapUtils;
import ee.ria.xroad.common.message.StaxEventSoapParserImpl;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.proxy.core.protocol.ProxyMessage;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import static ee.ria.xroad.common.util.EncoderUtils.encodeBase64;

/**
 * Custom SOAP parser that adds requestHash header element to the response.
 * <p>
 * This implementation captures raw XML bytes during parsing and collects metadata
 * (namespace prefix, whitespace pattern, existing requestHash presence). After parsing,
 * it writes the modified XML in segments using simple string indexOf operations,
 * avoiding XMLEventWriter overhead and regex complexity.
 */
@RequiredArgsConstructor
public final class ResponseStaxSoapParserImpl extends StaxEventSoapParserImpl {

    private final ProxyMessage requestMessage;

    private boolean inHeader;
    private boolean onQueryIdEnd = false;
    private boolean onRequestHashEnd = false;
    private int requestHashStart = -1;
    private int requestHashEnd = -1;
    private int queryIdStart = -1;
    private int queryIdEnd = -1;
    private String xrdPrefix = "";


    @Override
    protected byte[] postProcessXml(byte[] xmlBytes, String charset) throws XMLStreamException {
        if (queryIdStart < 0 && requestHashStart < 0) {
            return super.postProcessXml(xmlBytes, charset);
        }

        byte[] hashBytes = requestMessage.getSoap().getHash();
        String hash = encodeBase64(hashBytes);

        var algoUri = SoapUtils.getHashAlgoId();
        return RequestHashInjector.inject(
                algoUri.uri(),
                hash,
                xmlBytes,
                charset,
                queryIdStart, queryIdEnd, requestHashStart, requestHashEnd,
                xrdPrefix);
    }

    @Override
    protected void onNextEvent(XMLEvent currentEvent, XMLEvent previousEvent) throws XMLStreamException {

        if (onRequestHashEnd) {
            requestHashEnd = currentEvent.getLocation().getCharacterOffset();
            onRequestHashEnd = false;
        } else if (onQueryIdEnd) {
            queryIdEnd = currentEvent.getLocation().getCharacterOffset();
            onQueryIdEnd = false;
        }

        if (currentEvent.isStartElement()) {
            handleStartTag(currentEvent.asStartElement(), previousEvent);
        } else if (currentEvent.isEndElement()) {
            handleEndTag(currentEvent.asEndElement());
        }
    }

    private void handleStartTag(StartElement startElement, XMLEvent previousEvent) {
        if (QNAME_SOAP_HEADER.equals(startElement.getName())) {
            inHeader = true;
        } else if (inHeader && QNAME_XROAD_QUERY_ID.equals(startElement.getName())) {
            queryIdStart = startElement.getLocation().getCharacterOffset();
            xrdPrefix = startElement.getName().getPrefix();
        } else if (inHeader && QNAME_XROAD_REQUEST_HASH.equals(startElement.getName())) {
            requestHashStart = startElement.getLocation().getCharacterOffset();
        }
    }

    private void handleEndTag(EndElement endElement) {
        if (QNAME_SOAP_HEADER.equals(endElement.getName())) {
            inHeader = false;
        } else if (inHeader && QNAME_XROAD_QUERY_ID.equals(endElement.getName())) {
            onQueryIdEnd = true;
        } else if (inHeader && QNAME_XROAD_REQUEST_HASH.equals(endElement.getName())) {
            onRequestHashEnd = true;
        }
    }


}
