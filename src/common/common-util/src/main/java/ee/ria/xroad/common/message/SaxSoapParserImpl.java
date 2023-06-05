/**
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
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.identifier.XRoadObjectType;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.common.util.XmlUtils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.io.input.TeeInputStream;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DefaultHandler2;

import javax.xml.namespace.QName;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.soap.SOAPException;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.function.Consumer;

import static ee.ria.xroad.common.ErrorCodes.X_DUPLICATE_HEADER_FIELD;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_BODY;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_SOAP;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_XML;
import static ee.ria.xroad.common.ErrorCodes.X_MISSING_BODY;
import static ee.ria.xroad.common.ErrorCodes.X_MISSING_HEADER;
import static ee.ria.xroad.common.ErrorCodes.X_MISSING_HEADER_FIELD;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.message.SoapUtils.validateMimeType;
import static ee.ria.xroad.common.util.MimeUtils.UTF8;
import static ee.ria.xroad.common.util.MimeUtils.hasUtf8Charset;

/**
 * SOAP message parser that does not construct a DOM tree of the message.
 */
@Slf4j
public class SaxSoapParserImpl implements SoapParser {
    private static final String LEXICAL_HANDLER_PROPERTY = "http://xml.org/sax/properties/lexical-handler";

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

    protected static final String ATTR_OBJECT_TYPE = "objectType";
    protected static final String ATTR_ALGORITHM_ID = "algorithmId";
    protected static final String ATTR_ENCODING_STYLE = "encodingStyle";

    protected static final QName QNAME_SOAP_ENVELOPE = new QName(SoapUtils.NS_SOAPENV, ENVELOPE);
    protected static final QName QNAME_SOAP_HEADER = new QName(SoapUtils.NS_SOAPENV, HEADER);
    protected static final QName QNAME_SOAP_BODY = new QName(SoapUtils.NS_SOAPENV, BODY);
    protected static final QName QNAME_SOAP_FAULT = new QName(SoapUtils.NS_SOAPENV, FAULT);

    protected static final QName QNAME_XROAD_QUERY_ID = new QName(SoapHeader.NS_XROAD, QUERY_ID);
    protected static final QName QNAME_XROAD_USER_ID = new QName(SoapHeader.NS_XROAD, USER_ID);
    protected static final QName QNAME_XROAD_ISSUE = new QName(SoapHeader.NS_XROAD, ISSUE);
    protected static final QName QNAME_REPR_REPRESENTED_PARTY = new QName(SoapHeader.NS_REPR, REPRESENTED_PARTY);
    protected static final QName QNAME_XROAD_PROTOCOL_VERSION = new QName(SoapHeader.NS_XROAD, PROTOCOL_VERSION);
    protected static final QName QNAME_XROAD_REQUEST_HASH = new QName(SoapHeader.NS_XROAD, REQUEST_HASH);
    protected static final QName QNAME_XROAD_CLIENT = new QName(SoapHeader.NS_XROAD, CLIENT);
    protected static final QName QNAME_XROAD_SERVICE = new QName(SoapHeader.NS_XROAD, SERVICE);
    protected static final QName QNAME_XROAD_SECURITY_SERVER = new QName(SoapHeader.NS_XROAD, SECURITY_SERVER);

    protected static final QName QNAME_ID_INSTANCE = new QName(URI_IDENTIFIERS, INSTANCE);
    protected static final QName QNAME_ID_MEMBER_CLASS = new QName(URI_IDENTIFIERS, MEMBER_CLASS);
    protected static final QName QNAME_ID_MEMBER_CODE = new QName(URI_IDENTIFIERS, MEMBER_CODE);
    protected static final QName QNAME_ID_SUBSYSTEM_CODE = new QName(URI_IDENTIFIERS, SUBSYSTEM_CODE);
    protected static final QName QNAME_ID_SERVICE_CODE = new QName(URI_IDENTIFIERS, SERVICE_CODE);
    protected static final QName QNAME_ID_SERVICE_VERSION = new QName(URI_IDENTIFIERS, SERVICE_VERSION);
    protected static final QName QNAME_ID_SERVER_CODE = new QName(URI_IDENTIFIERS, SERVER_CODE);

    protected static final QName QNAME_PARTY_CLASS = new QName(URI_REPRESENTATION, PARTY_CLASS);
    protected static final QName QNAME_PARTY_CODE = new QName(URI_REPRESENTATION, PARTY_CODE);

    private static final String MISSING_HEADER_MESSAGE = "Malformed SOAP message: header missing";
    private static final String MISSING_SERVICE_MESSAGE =
            "Message header must contain service id";
    private static final String MISSING_HEADER_FIELD_MESSAGE = "Required field '%s' is missing";
    private static final String DUPLICATE_HEADER_MESSAGE = "SOAP header contains duplicate field '%s'";
    private static final String MISSING_BODY_MESSAGE = "Malformed SOAP message: body missing";
    private static final String INVALID_BODY_MESSAGE =
            "Malformed SOAP message: body must have exactly one child element";
    private static final String MISSING_ENVELOPE_MESSAGE = "Malformed SOAP message: envelope missing";

    private static final char[] CDATA_START = "<![CDATA[".toCharArray();
    private static final char[] CDATA_END = "]]>".toCharArray();
    private static final char[] COMMENT_START = "<!--".toCharArray();
    private static final char[] COMMENT_END = "-->".toCharArray();
    private static final char[] ENTITY_START = {'&'};
    private static final char[] ENTITY_END = {';'};

    private static final SAXParserFactory PARSER_FACTORY = createSaxParserFactory();

    @Override
    public Soap parse(String contentType, InputStream is) {
        String mimeType = MimeUtils.getBaseContentType(contentType);

        String charset = MimeUtils.getCharset(contentType);
        charset = StringUtils.isNotBlank(charset) ? charset : UTF8;

        // Explicitly check content type to produce better error code
        // for client.
        if (mimeType != null) {
            validateMimeType(mimeType);
        }

        try {
            return parseMessage(is, mimeType, contentType, charset);
        } catch (Exception e) {
            throw translateException(e);
        }
    }

    private Soap parseMessage(InputStream is, String mimeType, String contentType, String charset) throws Exception {
        log.trace("parseMessage({}, {})", mimeType, charset);

        ByteArrayOutputStream rawXml = new ByteArrayOutputStream();
        ByteArrayOutputStream processedXml = new ByteArrayOutputStream();

        InputStream proxyStream = excludeUtf8Bom(contentType, new TeeInputStream(is, rawXml));
        Writer outputWriter = new OutputStreamWriter(processedXml, charset);
        XRoadSoapHandler handler = handleSoap(outputWriter, proxyStream);

        CodedException fault = handler.getFault();
        if (fault != null) {
            return createSoapFault(charset, rawXml, fault);
        }

        byte[] xmlBytes = isProcessedXmlRequired() ? processedXml.toByteArray() : rawXml.toByteArray();

        return createSoapMessage(contentType, charset, handler, xmlBytes);
    }

    private XRoadSoapHandler handleSoap(Writer writer, InputStream inputStream)
            throws Exception {
        try (BufferedWriter out = new BufferedWriter(writer)) {
            XRoadSoapHandler handler = new XRoadSoapHandler(out);
            SAXParser saxParser = PARSER_FACTORY.newSAXParser();
            XMLReader xmlReader = saxParser.getXMLReader();
            xmlReader.setProperty(LEXICAL_HANDLER_PROPERTY, handler);
            // ensure both builtin entities and character entities are reported to the parser
            xmlReader.setFeature("http://apache.org/xml/features/scanner/notify-char-refs", true);
            xmlReader.setFeature("http://apache.org/xml/features/scanner/notify-builtin-refs", true);

            saxParser.parse(inputStream, handler);
            return handler;
        } catch (SAXException ex) {
            throw new SOAPException(ex);
        }
    }

    private static Soap createSoapMessage(String contentType, String charset,
            XRoadSoapHandler handler, byte[] xmlBytes) throws Exception {
        return new SoapMessageImpl(xmlBytes, charset, handler.getHeader(),
                null, handler.getServiceName(), handler.isRpc(), contentType);
    }

    private static Soap createSoapFault(String charset,
            ByteArrayOutputStream rawXml, CodedException fault) {
        return new SoapFault(fault.getFaultCode(), fault.getFaultString(),
                fault.getFaultActor(), fault.getFaultDetail(),
                rawXml.toByteArray(), charset);
    }

    @SneakyThrows
    private static SAXParserFactory createSaxParserFactory() {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://xml.org/sax/features/namespace-prefixes", true);
        // disable external entity parsing to avoid DOS attacks
        factory.setValidating(false);
        factory.setFeature(XmlUtils.FEATURE_DISALLOW_DOCTYPE, true);
        factory.setFeature(XmlUtils.FEATURE_EXTERNAL_GENERAL_ENTITIES, false);
        factory.setFeature(XmlUtils.FEATURE_EXTERNAL_PARAMETER_ENTITIES, false);
        return factory;
    }

    /**
     * Determines whether the raw XML of the SOAP message should be re-encoded
     * or if the original should be used in the output.
     * @return false by default
     */
    protected boolean isProcessedXmlRequired() {
        return false;
    }

    private InputStream excludeUtf8Bom(String contentType, InputStream soapStream) {
        return hasUtf8Charset(contentType) ? new BOMInputStream(soapStream) : soapStream;
    }

    @SneakyThrows
    protected void writeStartElementXml(String prefix, QName element, Attributes attributes, Writer writer) {
        writer.append('<');
        String localName = element.getLocalPart();
        String tag = StringUtils.isEmpty(prefix) ? localName : prefix + ":" + localName;
        writer.append(tag);
        for (int i = 0; i < attributes.getLength(); i++) {
            String escapedAttrValue = StringEscapeUtils.escapeXml11(attributes.getValue(i));
            writer.append(String.format(" %s=\"%s\"", attributes.getQName(i), escapedAttrValue));
        }
        writer.append('>');
    }

    @SneakyThrows
    protected void writeEndElementXml(String prefix, QName element, Attributes attributes, Writer writer) {
        writer.append("</");
        String localName = element.getLocalPart();
        String tag = StringUtils.isEmpty(prefix) ? localName : prefix + ":" + localName;
        writer.append(tag);
        writer.append('>');
    }

    @SneakyThrows
    protected void writeCharactersXml(char[] characters, int start, int length, Writer writer) {
        writer.write(characters, start, length);
    }

    protected SoapHeaderHandler getSoapHeaderHandler(SoapHeader header) {
        return new SoapHeaderHandler(header);
    }

    @RequiredArgsConstructor
    private class XRoadSoapHandler extends DefaultHandler2 {
        private static final String NAMESPACE_PREFIX_SEPARATOR = ":";

        private static final String XML_VERSION_ENCODING = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

        private final BufferedWriter out;

        private char[] xmlEntity;

        private Stack<XmlElementHandler> elementHandlers = new Stack<>();

        private SoapEnvelopeHandler envelopeHandler;

        @Getter
        private SoapHeader header;

        public String getServiceName() {
            return envelopeHandler != null ? envelopeHandler.getServiceName() : null;
        }

        public boolean isRpc() {
            return envelopeHandler != null && envelopeHandler.isRpc();
        }

        public CodedException getFault() {
            return envelopeHandler != null ? envelopeHandler.getFault() : null;
        }

        private void reset() {
            envelopeHandler = null;

            header = new SoapHeader();

            elementHandlers.clear();
        }

        @Override
        public void startDocument() {
            log.trace("startDocument()");
            reset();

            if (isProcessedXmlRequired()) {
                writeXmlDeclaration();
            }
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            QName element = new QName(uri, localName);

            if (elementHandlers.isEmpty()) {
                handleRootElement(attributes, element);
            } else {
                handleElement(attributes, element);
            }

            if (isProcessedXmlRequired()) {
                String prefix = findNamespacePrefix(qName);
                writeStartElementXml(prefix, element, attributes, out);
            }
        }

        private void handleElement(Attributes attributes, QName element) {
            XmlElementHandler elementHandler = elementHandlers.peek().getChildElementHandler(element);
            elementHandler.setAttributes(attributes);
            elementHandler.openTag();
            elementHandlers.push(elementHandler);
        }

        private void handleRootElement(Attributes attributes, QName element) {
            if (element.equals(QNAME_SOAP_ENVELOPE)) {
                envelopeHandler = new SoapEnvelopeHandler(getSoapHeaderHandler(header));
                envelopeHandler.setAttributes(attributes);
                envelopeHandler.openTag();
                elementHandlers.push(envelopeHandler);
            } else {
                throw new CodedException(X_INVALID_SOAP, MISSING_ENVELOPE_MESSAGE);
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {

            XmlElementHandler elementParser = elementHandlers.peek();
            elementParser.characters(ch, start, length);

            if (isProcessedXmlRequired()) {
                // Make sure XML entities are not resolved in processed XML
                if (xmlEntity != null) {
                    writeCharactersXml(ENTITY_START, 0, 1, out);
                    writeCharactersXml(xmlEntity, 0, xmlEntity.length, out);
                    writeCharactersXml(ENTITY_END, 0, 1, out);
                    xmlEntity = null;
                } else {
                    writeCharactersXml(ch, start, length, out);
                }
            }
        }

        @Override
        public void comment(char[] ch, int start, int length) {
            if (isProcessedXmlRequired()) {
                writeCharactersXml(COMMENT_START, 0, COMMENT_START.length, out);
                writeCharactersXml(ch, start, length, out);
                writeCharactersXml(COMMENT_END, 0, COMMENT_END.length, out);
            }
        }

        @Override
        public void startEntity(String name) {
            if (isProcessedXmlRequired()) {
                xmlEntity = name.toCharArray();
            }
        }

        @Override
        public void startCDATA() {
            if (isProcessedXmlRequired()) {
                writeCharactersXml(CDATA_START, 0, CDATA_START.length, out);
            }
        }

        @Override
        public void endCDATA() {
            if (isProcessedXmlRequired()) {
                writeCharactersXml(CDATA_END, 0, CDATA_END.length, out);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            XmlElementHandler elementHandler = elementHandlers.pop();
            Attributes attributes = elementHandler.getAttributes();
            elementHandler.valueInternal();
            elementHandler.closeTag();

            if (isProcessedXmlRequired()) {
                QName element = new QName(uri, localName);
                String prefix = findNamespacePrefix(qName);
                writeEndElementXml(prefix, element, attributes, out);
            }
        }

        @Override
        public void endDocument() {
            log.trace("endDocument()");
            if (isProcessedXmlRequired()) {
                writeNewLine();
            }
        }

        @Override
        public void warning(SAXParseException e) {
            log.warn(e.getMessage());
        }

        @Override
        public void error(SAXParseException e) {
            throw translateException(e);
        }

        @SneakyThrows
        private void writeNewLine() {
            out.newLine();
        }

        @SneakyThrows
        private void writeXmlDeclaration() {
            out.append(XML_VERSION_ENCODING);
            out.newLine();
        }

        private String findNamespacePrefix(String qName) {
            String prefix = "";
            if (qName.contains(NAMESPACE_PREFIX_SEPARATOR)) {
                prefix = qName.substring(0, qName.indexOf(NAMESPACE_PREFIX_SEPARATOR));
            }
            return prefix;
        }
    }

    private static void validateDuplicateHeader(QName qName,
            Object existing) {
        if (existing != null) {
            throw new CodedException(X_DUPLICATE_HEADER_FIELD, DUPLICATE_HEADER_MESSAGE, qName);
        }
    }

    /**
     * Abstract XML element handler for processing SAX parser events. Includes
     * callbacks for beginning of element tag, character content and end element
     * of element tag. By default returns a NOOP handler for child elements.
     */
    protected abstract static class XmlElementHandler {

        private StringBuilder valueBuffer = new StringBuilder();

        @Getter
        @Setter
        private Attributes attributes;

        /**
         * Returns a handler for child elements of the given qualified name.
         * @param element the element's qualified name
         * @return the handler for the given child element
         */
        protected XmlElementHandler getChildElementHandler(QName element) {
            return NoOpHandler.INSTANCE;
        }

        /**
         * Called when the parser encounters an element start event.
         */
        protected void openTag() {
        }

        /**
         * Called when the parser encounters characters between this
         * element's start and end tags. By default collects characters
         * into an internal buffer.
         * @param ch the character array
         * @param start start of the relevant region
         * @param length content length
         */
        protected void characters(char[] ch, int start, int length) {
            valueBuffer.append(ch, start, length);
        }

        protected final void valueInternal() {
            String value = "";
            if (valueBuffer.length() > 0) {
                value = valueBuffer.toString();
                valueBuffer.setLength(0);
            }
            value(value);
        }

        /**
         * Called when parser finishes reading the handled element's value.
         * @param value the element's value
         */
        protected void value(String value) {
        }

        /**
         * Called when the parser encounters an element end event.
         */
        protected void closeTag() {
        }

        /**
         * Create a generic handler that will apply a function to
         * the string value of the handled element.
         * @param stringValueHandler the function that will be applied
         * @return the created element handler
         */
        public static XmlElementHandler createValueElementHandler(Consumer<String> stringValueHandler) {
            return new XmlElementHandler() {
                @Override
                protected void value(String val) {
                    stringValueHandler.accept(val);
                }
            };
        }

    }

    /**
     * Handler that ignores the element in it's entirety.
     */
    private static class NoOpHandler extends XmlElementHandler {

        public static final XmlElementHandler INSTANCE = new NoOpHandler();

        @Override
        protected void characters(char[] ch, int start, int length) {
            // ignore character content
        }
    }

    /**
     * Handler for the SOAP envelope, has child handlers for the
     * SOAP header and the SOAP body.
     */
    @RequiredArgsConstructor
    private static class SoapEnvelopeHandler extends XmlElementHandler {
        private final SoapHeaderHandler headerHandler;
        private SoapBodyHandler bodyHandler;

        @Getter
        private boolean rpc;

        public CodedException getFault() {
            return bodyHandler != null ? bodyHandler.getFault() : null;
        }

        public String getServiceName() {
            return bodyHandler != null ? bodyHandler.getServiceName() : null;
        }

        @Override
        protected void openTag() {
            rpc = URI_ENCODING.equals(getAttributes().getValue(URI_ENVELOPE, ATTR_ENCODING_STYLE));
        }

        @Override
        protected XmlElementHandler getChildElementHandler(QName element) {
            if (element.equals(QNAME_SOAP_HEADER)) {
                return headerHandler;
            } else if (element.equals(QNAME_SOAP_BODY)) {
                bodyHandler = new SoapBodyHandler();
                return bodyHandler;
            }
            return super.getChildElementHandler(element);
        }

        @Override
        protected void closeTag() {
            if (getFault() == null) {
                validateHeader();
                validateBody();
            }
        }

        private void validateHeader() {
            if (!headerHandler.isFinished()) {
                throw new CodedException(X_MISSING_HEADER, MISSING_HEADER_MESSAGE);
            }
            SoapHeader header = headerHandler.getHeader();
            if (header.getProtocolVersion() == null) {
                onMissingRequiredField(PROTOCOL_VERSION);
            }
            if (header.getClient() == null) {
                onMissingRequiredField(CLIENT);
            }
            if (header.getQueryId() == null) {
                onMissingRequiredField(QUERY_ID);
            }
            if (getService() == null) {
                throw new CodedException(X_MISSING_HEADER_FIELD, MISSING_SERVICE_MESSAGE);
            }
        }

        private void onMissingRequiredField(String fieldName) {
            throw new CodedException(X_MISSING_HEADER_FIELD, MISSING_HEADER_FIELD_MESSAGE, fieldName);
        }

        private void validateBody() {
            if (bodyHandler == null) {
                throw new CodedException(X_MISSING_BODY, MISSING_BODY_MESSAGE);
            }
            if (getServiceName() == null) {
                throw new CodedException(X_INVALID_BODY, INVALID_BODY_MESSAGE);
            }
            ServiceId service = getService();
            SoapUtils.validateServiceName(service.getServiceCode(), getServiceName());
        }

        private ServiceId getService() {
            SoapHeader header = headerHandler.getHeader();
            return header.getService();
        }

    }

    /**
     * Handler for the SOAP header. Parses XRoad protocol header
     * elements and populates the header object given to it.
     * Throws an exception if duplicate elements are encountered.
     */
    @RequiredArgsConstructor
    protected static class SoapHeaderHandler extends XmlElementHandler {
        @Getter
        protected final SoapHeader header;

        @Getter
        private boolean finished;

        /**
         * Called when a security server header has been parsed.
         * @param securityServerId the parsed client ID
         */
        protected void onSecurityServer(SecurityServerId.Conf securityServerId) {
            header.setSecurityServer(securityServerId);
        }

        /**
         * Called when a client header has been parsed.
         * @param clientId the parsed client ID
         */
        protected void onClient(ClientId.Conf clientId) {
            header.setClient(clientId);
        }

        /**
         * Called when a service header has been parsed.
         * @param serviceId the parsed service ID
         */
        protected void onService(ServiceId.Conf serviceId) {
            header.setService(serviceId);
        }

         /**
         * Called when a represented party header has been paresed.
         * @param representedParty the represented party
         */
        protected void onRepresentedParty(RepresentedParty representedParty) {
            header.setRepresentedParty(representedParty);
        }

        @Override
        protected XmlElementHandler getChildElementHandler(QName element) {
            if (element.equals(QNAME_XROAD_QUERY_ID)) {
                validateDuplicateHeader(element, header.getQueryId());
                return createValueElementHandler(header::setQueryId);
            } else if (element.equals(QNAME_XROAD_USER_ID)) {
                validateDuplicateHeader(element, header.getUserId());
                return createValueElementHandler(header::setUserId);
            } else if (element.equals(QNAME_XROAD_ISSUE)) {
                validateDuplicateHeader(element, header.getIssue());
                return createValueElementHandler(header::setIssue);
            } else if (element.equals(QNAME_XROAD_PROTOCOL_VERSION)) {
                validateDuplicateHeader(element, header.getProtocolVersion());
                return createValueElementHandler(this::setProtocolVersion);
            } else if (element.equals(QNAME_XROAD_CLIENT)) {
                validateDuplicateHeader(element, header.getClient());
                return new XRoadClientHeaderHandler(this::onClient);
            } else if (element.equals(QNAME_XROAD_SERVICE)) {
                validateDuplicateHeader(element, header.getService());
                return new XRoadServiceHeaderHandler(this::onService);
            } else if (element.equals(QNAME_REPR_REPRESENTED_PARTY)) {
                validateDuplicateHeader(element, header.getRepresentedParty());
                return new XRoadRepresentedPartyHeaderHandler(this::onRepresentedParty);
            } else if (element.equals(QNAME_XROAD_SECURITY_SERVER)) {
                validateDuplicateHeader(element, header.getSecurityServer());
                return new XRoadSecurityServerHeaderHandler(this::onSecurityServer);
            } else if (element.equals(QNAME_XROAD_REQUEST_HASH)) {
                validateDuplicateHeader(element, header.getRequestHash());
                return new XRoadRequestHashElementHandler();
            }
            return super.getChildElementHandler(element);
        }

        @SneakyThrows
        private void setProtocolVersion(String val) {
            header.setProtocolVersion(new ProtocolVersion(val));
        }

        @Override
        @SneakyThrows
        protected void closeTag() {
            finished = true;
        }

        private class XRoadRequestHashElementHandler extends XmlElementHandler {
            private String hashAlgoId;

            @Override
            public void openTag() {
                hashAlgoId =  getAttributes().getValue("", ATTR_ALGORITHM_ID);
            }

            @Override
            protected void value(String val) {
                header.setRequestHash(new RequestHash(hashAlgoId, val));
            }
        }
    }

    /**
     * Generic handler for XRoad protocol identifiers, holds identifier
     * element values in a internal map and parses only elements returned
     * by the getAllowedChildElements method.
     */
    @RequiredArgsConstructor
    private abstract static class XRoadIdentifierHeaderHandler extends XmlElementHandler {
        private final List<XRoadObjectType> expected;

        private Map<QName, String> identifierValues = new HashMap<>();

        protected String getValue(QName key) {
            return identifierValues.get(key);
        }

        protected void setValue(QName key, String value) {
            identifierValues.put(key, value);
        }

        @Override
        public void openTag() {
            XRoadObjectType objectType = getObjectType();
            if (!expected.contains(objectType)) {
                throw new CodedException(X_INVALID_XML, "Unexpected objectType: %s", objectType);
            }
        }

        protected abstract List<QName> getAllowedChildElements();

        @Override
        protected XmlElementHandler getChildElementHandler(QName element) {
            if (getAllowedChildElements().contains(element)) {
                validateDuplicateHeader(element, getValue(element));
                return new IdentifierElementHandler(element);
            }
            return super.getChildElementHandler(element);
        }

        @RequiredArgsConstructor
        private class IdentifierElementHandler extends XmlElementHandler {
            private final QName key;

            @Override
            protected void openTag() {
                validateDuplicateHeader(key, getValue(key));
            }

            @Override
            protected void value(String val) {
                setValue(key, val);
            }
        }

        private XRoadObjectType getObjectType() {
            String objectType = getAttributes().getValue(URI_IDENTIFIERS, ATTR_OBJECT_TYPE);
            if (objectType == null) {
                throw new CodedException(X_INVALID_XML, "Missing objectType attribute");
            }

            try {
                return XRoadObjectType.valueOf(objectType);
            } catch (IllegalArgumentException e) {
                throw new CodedException(X_INVALID_XML, "Unknown objectType: %s", objectType);
            }
        }
    }

    /**
     * Handler for the XRoad protocol client header.
     */
    private static class XRoadClientHeaderHandler extends XRoadIdentifierHeaderHandler {

        protected static final List<QName> CLIENT_ID_PARTS = Arrays.asList(
                QNAME_ID_INSTANCE, QNAME_ID_MEMBER_CLASS, QNAME_ID_MEMBER_CODE,
                QNAME_ID_SUBSYSTEM_CODE);

        private final Consumer<ClientId.Conf> onClientCallback;

        XRoadClientHeaderHandler(Consumer<ClientId.Conf> callback) {
            super(Arrays.asList(XRoadObjectType.MEMBER, XRoadObjectType.SUBSYSTEM));
            this.onClientCallback = callback;
        }

        @Override
        protected List<QName> getAllowedChildElements() {
            return CLIENT_ID_PARTS;
        }

        @Override
        public void closeTag() {
            onClientCallback.accept(ClientId.Conf.create(
                    getValue(QNAME_ID_INSTANCE),
                    getValue(QNAME_ID_MEMBER_CLASS),
                    getValue(QNAME_ID_MEMBER_CODE),
                    getValue(QNAME_ID_SUBSYSTEM_CODE)));
        }
    }

    /**
     * Handler for the XRoad protocol service header.
     */
    private static class XRoadServiceHeaderHandler extends XRoadIdentifierHeaderHandler {

        protected static final List<QName> SERVICE_ID_PARTS = Arrays.asList(
                QNAME_ID_INSTANCE, QNAME_ID_MEMBER_CLASS, QNAME_ID_MEMBER_CODE,
                QNAME_ID_SUBSYSTEM_CODE, QNAME_ID_SERVICE_CODE,
                QNAME_ID_SERVICE_VERSION);

        private final Consumer<ServiceId.Conf> onServiceCallback;

        XRoadServiceHeaderHandler(Consumer<ServiceId.Conf> callback) {
            super(Collections.singletonList(XRoadObjectType.SERVICE));
            this.onServiceCallback = callback;
        }

        @Override
        protected List<QName> getAllowedChildElements() {
            return SERVICE_ID_PARTS;
        }

        @Override
        protected void closeTag() {
            onServiceCallback.accept(ServiceId.Conf.create(
                    getValue(QNAME_ID_INSTANCE),
                    getValue(QNAME_ID_MEMBER_CLASS),
                    getValue(QNAME_ID_MEMBER_CODE),
                    getValue(QNAME_ID_SUBSYSTEM_CODE),
                    getValue(QNAME_ID_SERVICE_CODE),
                    getValue(QNAME_ID_SERVICE_VERSION)));
        }
    }

    /**
     * Handler for the XRoad protocol extension represented party header.
     */
    @RequiredArgsConstructor
    private static class XRoadRepresentedPartyHeaderHandler
            extends XmlElementHandler {
        protected static final List<QName> REPRESENTED_PARTY_PARTS =
                Arrays.asList(QNAME_PARTY_CLASS, QNAME_PARTY_CODE);

        private final Consumer<RepresentedParty> onRepresentedPartyCallback;

        private Map<QName, String> representedPartyValues = new HashMap<>();

        protected String getValue(QName key) {
            return representedPartyValues.get(key);
        }

        protected void setValue(QName key, String value) {
            representedPartyValues.put(key, value);
        }

        private List<QName> getAllowedChildElements() {
            return REPRESENTED_PARTY_PARTS;
        }

        @Override
        protected XmlElementHandler getChildElementHandler(QName element) {
            if (getAllowedChildElements().contains(element)) {
                validateDuplicateHeader(element, getValue(element));

                return new RepresentedPartyElementHandler(element);
            }

            return super.getChildElementHandler(element);
        }

        @RequiredArgsConstructor
        private class RepresentedPartyElementHandler extends XmlElementHandler {
            private final QName key;

            @Override
            protected void openTag() {
                validateDuplicateHeader(key, getValue(key));
            }

            @Override
            protected void value(String val) {
                setValue(key, val);
            }
        }

        @Override
        public void closeTag() {
            onRepresentedPartyCallback.accept(new RepresentedParty(
                    getValue(QNAME_PARTY_CLASS),
                    getValue(QNAME_PARTY_CODE)));
        }
    }

    /**
     * Handler for the XRoad protocol security server header.
     */
    private static class XRoadSecurityServerHeaderHandler extends XRoadIdentifierHeaderHandler {

        protected static final List<QName> SECURITY_SERVER_ID_PARTS =
                Arrays.asList(QNAME_ID_INSTANCE, QNAME_ID_MEMBER_CLASS,
                        QNAME_ID_MEMBER_CODE, QNAME_ID_SERVER_CODE);

        private final Consumer<SecurityServerId.Conf> onServiceCallback;

        XRoadSecurityServerHeaderHandler(Consumer<SecurityServerId.Conf> callback) {
            super(Collections.singletonList(XRoadObjectType.SERVER));
            this.onServiceCallback = callback;
        }

        @Override
        protected List<QName> getAllowedChildElements() {
            return SECURITY_SERVER_ID_PARTS;
        }

        @Override
        protected void closeTag() {
            onServiceCallback.accept(SecurityServerId.Conf.create(
                    getValue(QNAME_ID_INSTANCE),
                    getValue(QNAME_ID_MEMBER_CLASS),
                    getValue(QNAME_ID_MEMBER_CODE),
                    getValue(QNAME_ID_SERVER_CODE)));
        }
    }

    /**
     * Handler for the SOAP body, looks at the root element's name for
     * the service code or the SOAP fault element, should it exists.
     * Ignores everything else.
     */
    private static class SoapBodyHandler extends XmlElementHandler {
        private SoapFaultHandler soapFaultHandler;

        @Getter
        private CodedException fault;

        @Getter
        private String serviceName;

        @Override
        protected XmlElementHandler getChildElementHandler(QName element) {
            if (element.equals(QNAME_SOAP_FAULT)) {
                soapFaultHandler = new SoapFaultHandler();
                return soapFaultHandler;
            } else if (serviceName == null) {
                // If no body elements have been encountered yet we assume
                // the first one to be the request wrapper element
                serviceName = element.getLocalPart();
            } else {
                // If one body element has already been closed then we know
                // it's name to be the service name and expect no more top
                // level elements in the body
                throw new CodedException(X_INVALID_BODY, INVALID_BODY_MESSAGE);
            }
            return super.getChildElementHandler(element);
        }

        @Override
        protected void closeTag() {
            if (soapFaultHandler != null) {
                fault = CodedException.fromFault(
                        soapFaultHandler.getFaultCode(),
                        soapFaultHandler.getFaultString(),
                        soapFaultHandler.getFaultActor(),
                        soapFaultHandler.getFaultDetail(), null);
            }
        }
    }

    /**
     * Handler that extracts information from the SOAP fault.
     */
    private static class SoapFaultHandler extends XmlElementHandler {
        private static final String DETAIL = "faultDetail";

        @Getter
        private String faultCode;
        @Getter
        private String faultString;
        @Getter
        private String faultActor;
        @Getter
        private String faultDetail;

        @Override
        protected XmlElementHandler getChildElementHandler(QName element) {
            if (element.getLocalPart().equals(FAULT_CODE)) {
                return createValueElementHandler(val -> faultCode = val);
            } else if (element.getLocalPart().equals(FAULT_STRING)) {
                return createValueElementHandler(val -> faultString = val);
            } else if (element.getLocalPart().equals(FAULT_ACTOR)) {
                return createValueElementHandler(val -> faultActor = val);
            } else if (element.getLocalPart().equals(FAULT_DETAIL)) {
                return createFaultDetailHandler();
            }
            return super.getChildElementHandler(element);
        }

        private XmlElementHandler createFaultDetailHandler() {
            return new XmlElementHandler() {
                @Override
                protected XmlElementHandler getChildElementHandler(QName element) {
                    if (element.getLocalPart().equals(DETAIL)) {
                        return createValueElementHandler(val -> faultDetail = val);
                    }
                    return super.getChildElementHandler(element);
                }

                @Override
                protected void value(String val) {
                    if (StringUtils.isEmpty(faultDetail)) {
                        faultDetail = val;
                    }
                }
            };
        }

    }
}
