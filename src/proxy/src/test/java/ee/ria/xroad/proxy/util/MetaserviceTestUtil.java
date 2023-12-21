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
package ee.ria.xroad.proxy.util;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.ProtocolVersion;
import ee.ria.xroad.common.message.SoapHeader;
import ee.ria.xroad.common.metadata.ObjectFactory;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.XmlUtils;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.soap.MessageFactory;
import jakarta.xml.soap.SOAPBody;
import jakarta.xml.soap.SOAPEnvelope;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPHeader;
import jakarta.xml.soap.SOAPMessage;
import jakarta.xml.soap.SOAPPart;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.hibernate.query.Query;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.wsdl.BindingOperation;
import javax.wsdl.Definition;
import javax.wsdl.Port;
import javax.wsdl.Service;
import javax.wsdl.extensions.soap.SOAPAddress;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.stream.StreamSource;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.conf.serverconf.ServerConfDatabaseCtx.doInTransaction;
import static ee.ria.xroad.common.util.MimeUtils.UTF8;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.io.IOUtils.toInputStream;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Small util class for metaservice unit- and integration tests
 */
public final class MetaserviceTestUtil {

    public static final String PARAM_INSTANCE_IDENTIFIER = "xRoadInstance";
    private static final String NS_PRODUCER = "http://test.x-road.fi/producer";
    public static final QName REQUEST = new QName(NS_PRODUCER, "request");
    public static final QName GET_WSDL_REQUEST = new QName(NS_PRODUCER, "getWsdl");
    public static final QName ALLOWED_METHODS_REQUEST = new QName(NS_PRODUCER, "allowedMethods");
    public static final QName LIST_METHODS_REQUEST = new QName(NS_PRODUCER, "listMethods");
    public static final QName GET_METRICS_REQUEST = new QName(NS_PRODUCER, "getSecurityServerMetrics");

    private static Unmarshaller unmarshaller;
    private static Marshaller marshaller;
    private static DocumentBuilderFactory documentBuilderFactory;

    private MetaserviceTestUtil() {
    }

    static {
        try {
            unmarshaller = JAXBContext.newInstance(ObjectFactory.class).createUnmarshaller();
            marshaller = JAXBContext.newInstance(ObjectFactory.class, SoapHeader.class).createMarshaller();
            documentBuilderFactory = XmlUtils.createDocumentBuilderFactory();
        } catch (JAXBException e) {
            throw new IllegalStateException("Creating instance failed", e);
        }
    }


    public static final String DUMMY_QUERY_FILE = "dummy.query";

    private static final List<String> CONTENT_TYPES = Arrays.asList(MimeTypes.TEXT_XML_UTF8,
            "text/xml;charset=UTF-8",
            "text/xml;charset=utf-8");

    public static List<String> xmlUtf8ContentTypes() {
        return CONTENT_TYPES;
    }

    /** Try to extract a single element of type T from the Soap Body, of class clazz.
     * @param body the {@link SOAPBody}
     * @param clazz the class of type T
     * @param <T> the {@link JAXBElement} value to extract, like {@link ee.ria.xroad.common.metadata.MethodListType}
     * @return the resulting element of type T
     * @throws JAXBException if unexpected errors occur during unmarshalling
     * @throws AssertionError if more than one element of the type T
     */
    public static <T> T verifyAndGetSingleBodyElementOfType(SOAPBody body, Class<T> clazz) throws JAXBException {

        return verifyAndGetSingleBodyElementOfType(body, clazz, () -> unmarshaller);
    }

    /** Try to extract a single element of type T from the Soap Body, of class clazz.
     * @param body the {@link SOAPBody}
     * @param clazz the class of type T
     * @param unmarshallerSupplier a {@link Supplier} for the unmarshaller. Needed if this util class does not
     *                             know of the class you want to unmarshall
     * @param <T> the {@link JAXBElement} value to extract, like {@link ee.ria.xroad.common.metadata.MethodListType}
     * @return the resulting element of type T
     * @throws JAXBException if unexpected errors occur during unmarshalling
     */
    public static <T> T verifyAndGetSingleBodyElementOfType(SOAPBody body, Class<T> clazz,
                                                            Supplier<Unmarshaller> unmarshallerSupplier)
            throws JAXBException {

        NodeList list = body.getChildNodes();

        List<Element> elements = new ArrayList<>();
        for (int i = 0; i < list.getLength(); i++) {
            Node node = list.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                elements.add((Element) node);
            }
        }

        assertThat("Was expecting a single element", elements.size(), is(1));
        JAXBElement<T> element = unmarshallerSupplier.get().unmarshal(elements.get(0), clazz);
        return element.getValue();
    }

    public static ServiceId.Conf createService(String serviceCode) {
        return ServiceId.Conf.create("EE", "BUSINESS",
                "consumer", "SUB", serviceCode);
    }

    /** The definition to extract {@link BindingOperation} names from
     * @param definition
     * @return List of the names of the {@link BindingOperation}s in the given definition
     */
    @SuppressWarnings("unchecked")
    public static List<String> parseOperationNamesFromWSDLDefinition(Definition definition) {
        Collection<Service> services = definition.getServices().values();

        // note that these return raw type collections
        return services.stream()
                .flatMap(service -> ((Collection<Port>)service.getPorts().values()).stream())
                .flatMap(port -> ((List<BindingOperation>)port.getBinding().getBindingOperations()).stream())
                .map(BindingOperation::getName)
                .collect(Collectors.toList());
    }

    /**
     * Return all endpoint URLs for the definition
     * @param definition
     * @return
     */
    public static List<String> parseEndpointUrlsFromWSDLDefinition(Definition definition) {
        @SuppressWarnings("unchecked") Collection<Service> services = definition.getServices().values();
        List<String> endpointUrls = new ArrayList<>();
        for (Service service: services) {
            for (Object portObject: service.getPorts().values()) {
                Port port = (Port) portObject;
                for (Object extensibilityElement: port.getExtensibilityElements()) {
                    if (extensibilityElement instanceof SOAPAddress) {
                        endpointUrls.add(((SOAPAddress)extensibilityElement).getLocationURI());
                    }
                }
            }
        }
        return endpointUrls;
    }


    /** Merge xroad-specific {@link SoapHeader} to the generic {@link SOAPHeader}
     * @param header
     * @param xrHeader
     * @throws JAXBException
     * @throws ParserConfigurationException
     * @throws SOAPException
     */
    public static void mergeHeaders(SOAPHeader header, SoapHeader xrHeader) throws JAXBException,
            ParserConfigurationException, SOAPException {



        Document document = documentBuilderFactory.newDocumentBuilder().newDocument();
        final DocumentFragment documentFragment = document.createDocumentFragment();
        // marshalling on the header would add the xroad header as a child of the header
        // (i.e. two nested header elements)
        marshaller.marshal(xrHeader, documentFragment);

        Document headerDocument = header.getOwnerDocument();
        Node xrHeaderElement = documentFragment.getFirstChild();

        assertTrue("test setup failed: assumed had header element but did not",
                xrHeaderElement.getNodeType() == Node.ELEMENT_NODE
                        && xrHeaderElement.getLocalName().equals("Header"));

        final NamedNodeMap attributes = xrHeaderElement.getAttributes();

        if (attributes != null) {
            for (int i = 0; i < attributes.getLength(); i++) {
                final Attr attribute = (Attr) attributes.item(i);
                header.setAttributeNodeNS((Attr) headerDocument.importNode(attribute, false));
            }
        }

        final NodeList childNodes = xrHeaderElement.getChildNodes();

        if (childNodes != null) {
            for (int i = 0; i < childNodes.getLength(); i++) {
                final Node node = childNodes.item(i);
                header.appendChild(headerDocument.importNode(node, true));
            }
        }

    }

    /** Stub class for {@link ServletOutputStream}. For mocking Servlet interactions.
     * */
    public static class StubServletOutputStream extends ServletOutputStream {

        private ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        public StreamSource getResponseSource() {
            return new StreamSource(getAsInputStream());
        }

        public InputStream getAsInputStream() {
            return new BufferedInputStream(new ByteArrayInputStream(getAsBytes()));
        }

        public byte[] getAsBytes() {
            return outputStream.toByteArray();
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
        }

        @Override
        public void write(int b) throws IOException {
            outputStream.write(b);
        }
    }

    /**
     * A class to match {@link CodedException} codes with Hamcrest.
     */
    public static class CodedExceptionMatcher extends TypeSafeMatcher<CodedException> {

        private final String faultCode;

        public static CodedExceptionMatcher faultCodeEquals(String faultCode) {
            return new CodedExceptionMatcher(faultCode);
        }

        protected CodedExceptionMatcher(String faultCode) {
            this.faultCode = faultCode;
        }

        @Override
        protected boolean matchesSafely(CodedException ex) {
            return faultCode.equals(ex.getFaultCode());
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("expects faultCode ").appendValue(faultCode);
        }

        @Override
        protected void describeMismatchSafely(CodedException ex, Description mismatchDescription) {
            mismatchDescription.appendText("was ").appendValue(ex.getFaultCode());
        }
    }

    /** Clean the database (You are using this from a test, right?)
     */
    public static void cleanDB() throws Exception {
        doInTransaction(session -> {
            Query q = session.createNativeQuery("TRUNCATE SCHEMA public AND COMMIT");
            q.executeUpdate();
            return null;
        });
    }

    /**
     * A builder of SOAP messages as {@link String} or {@link InputStream} for test use.
     */
    public static class TestSoapBuilder {

        private ClientId clientId;
        private ServiceId serviceId;
        private SoapBodyModifier bodyModifier;

        private SoapHeader xroadSoapHeader;
        private static MessageFactory messageFactory;


        static {
            try {
                messageFactory = MessageFactory.newInstance();
            } catch (SOAPException e) {
                throw new IllegalStateException("Could not create message factory", e);
            }
        }


        public TestSoapBuilder withClient(ClientId client) {
            this.clientId = client;
            return this;
        }

        public TestSoapBuilder withService(ServiceId service) {
            this.serviceId = service;
            return this;
        }

        public TestSoapBuilder withModifiedBody(SoapBodyModifier soapBodyModifier) {
            this.bodyModifier = soapBodyModifier;
            return this;
        }

        /** Create a {@link SOAPMessage} from the input and return it as an inputstream
         * @return an {@link InputStream} to the SOAPMessage content
         * @throws Exception
         */
        public String buildAsString() throws Exception {
            requireNonNull(this.serviceId);
            requireNonNull(this.clientId);
            if (this.bodyModifier == null) {
                this.bodyModifier = soapBody -> {
                };
            }

            ProtocolVersion protocolVersion = new ProtocolVersion();
            protocolVersion.setVersion("4.0");

            this.xroadSoapHeader = new SoapHeader();
            xroadSoapHeader.setService(this.serviceId);
            xroadSoapHeader.setClient(this.clientId);
            xroadSoapHeader.setProtocolVersion(protocolVersion);
            xroadSoapHeader.setQueryId(Long.toString(System.currentTimeMillis()));
            xroadSoapHeader.setUserId("testUser");

            SOAPMessage soapMsg = messageFactory.createMessage();

            SOAPPart part = soapMsg.getSOAPPart();
            SOAPEnvelope envelope = part.getEnvelope();

            mergeHeaders(envelope.getHeader(), xroadSoapHeader);

            SOAPBody body = envelope.getBody();
            this.bodyModifier.modify(body);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            soapMsg.writeTo(out);

            return out.toString(UTF8);
        }

        public InputStream buildAsInputStream() throws Exception {
            return toInputStream(buildAsString(), UTF8);
        }

        /**
         * Use to modify the {@link SOAPBody} if necessary.
         */
        @FunctionalInterface
        public interface SoapBodyModifier {
            void modify(SOAPBody soapBody) throws SOAPException, JAXBException;
        }
    }
}
