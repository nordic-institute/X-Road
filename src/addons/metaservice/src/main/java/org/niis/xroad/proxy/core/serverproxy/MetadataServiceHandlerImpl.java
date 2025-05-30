/*
 * The MIT License
 *
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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.JaxbUtils;
import ee.ria.xroad.common.message.MultipartSoapMessageEncoder;
import ee.ria.xroad.common.message.SimpleSoapEncoder;
import ee.ria.xroad.common.message.SoapMessageEncoder;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapParserImpl;
import ee.ria.xroad.common.message.SoapUtils;
import ee.ria.xroad.common.message.SoapUtils.SOAPCallback;
import ee.ria.xroad.common.metadata.MethodListType;
import ee.ria.xroad.common.metadata.ObjectFactory;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.XmlUtils;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.soap.SOAPMessage;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.proxy.core.common.WsdlRequestData;
import org.niis.xroad.proxy.core.protocol.ProxyMessage;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.impl.ServerConfDatabaseCtx;
import org.niis.xroad.serverconf.impl.dao.ServiceDescriptionDAOImpl;
import org.niis.xroad.serverconf.impl.mapper.ServiceDescriptionMapper;
import org.niis.xroad.serverconf.model.DescriptionType;
import org.niis.xroad.serverconf.model.ServiceDescription;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DefaultHandler2;
import org.xml.sax.ext.LexicalHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static ee.ria.xroad.common.ErrorCodes.X_INVALID_REQUEST;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_SERVICE_TYPE;
import static ee.ria.xroad.common.ErrorCodes.X_UNKNOWN_SERVICE;
import static org.niis.xroad.proxy.core.util.MetadataRequests.ALLOWED_METHODS;
import static org.niis.xroad.proxy.core.util.MetadataRequests.GET_WSDL;
import static org.niis.xroad.proxy.core.util.MetadataRequests.LIST_METHODS;

@Slf4j
class MetadataServiceHandlerImpl extends AbstractServiceHandler {

    static final JAXBContext JAXB_CTX = initJaxbCtx();
    static final ObjectFactory OBJECT_FACTORY = new ObjectFactory();

    public static final String WSDL_ENDPOINT_ADDRESS = "http://example.org/xroad-endpoint";

    private final ByteArrayOutputStream responseOut =
            new ByteArrayOutputStream();


    private SoapMessageImpl requestMessage;
    private SoapMessageEncoder responseEncoder;

    private final HttpClientCreator wsdlHttpClientCreator;

    private static final SAXTransformerFactory TRANSFORMER_FACTORY = createSaxTransformerFactory();

    protected MetadataServiceHandlerImpl(ServerConfProvider serverConfProvider, GlobalConfProvider globalConfProvider) {
        super(serverConfProvider, globalConfProvider);
        wsdlHttpClientCreator = new HttpClientCreator(serverConfProvider);
    }

    private static SAXTransformerFactory createSaxTransformerFactory() {
        try {
            SAXTransformerFactory factory = (SAXTransformerFactory) TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            return factory;
        } catch (TransformerConfigurationException e) {
            throw new RuntimeException("unable to create SAX transformer factory", e);
        }
    }

    @Override
    public boolean shouldVerifyAccess() {
        return false;
    }

    @Override
    public boolean shouldVerifySignature() {
        return true;
    }

    @Override
    public boolean shouldLogSignature() {
        return true;
    }

    @Override
    @SneakyThrows
    public boolean canHandle(ServiceId requestServiceId,
                             ProxyMessage requestProxyMessage) {

        requestMessage = requestProxyMessage.getSoap();

        return switch (requestServiceId.getServiceCode()) {
            case LIST_METHODS, ALLOWED_METHODS, GET_WSDL -> {
                try (var in = new PipedInputStream()) {
                    @SuppressWarnings("java:S2095")// out is closed in try-with-resources
                    var out = new PipedOutputStream(in);
                    Thread.startVirtualThread(() -> {
                        try (out) {
                            requestProxyMessage.writeSoapContent(out);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to write soap content", e);
                        }
                    });
                    requestMessage = (SoapMessageImpl) new SoapParserImpl().parse(requestProxyMessage.getSoapContentType(), in);
                }
                yield true;
            }
            default -> false;
        };
    }

    @Override
    public void startHandling(RequestWrapper servletRequest,
                              ProxyMessage proxyRequestMessage, HttpClient opMonitorClient,
                              OpMonitoringData opMonitoringData) throws Exception {

        final String serviceCode = requestMessage.getService().getServiceCode();

        // Only get wsdl needs to be a multipart message
        responseEncoder = GET_WSDL.equals(serviceCode)
                ? new MultipartSoapMessageEncoder(responseOut)
                : new SimpleSoapEncoder(responseOut);

        // It's required that in case of metadata service (where SOAP message is
        // not forwarded) the requestOutTs must be equal with the requestInTs
        // and the responseInTs must be equal with the responseOutTs.
        opMonitoringData.setRequestOutTs(opMonitoringData.getRequestInTs());
        opMonitoringData.setAssignResponseOutTsToResponseInTs(true);
        opMonitoringData.setServiceType(DescriptionType.WSDL.name());

        switch (serviceCode) {
            case LIST_METHODS -> handleListMethods(requestMessage);
            case ALLOWED_METHODS -> handleAllowedMethods(requestMessage);
            case GET_WSDL -> handleGetWsdl(requestMessage);
            default -> {
            }
        }
    }

    @Override
    public void finishHandling() throws Exception {
        // nothing to do
    }

    @Override
    public String getResponseContentType() {
        return responseEncoder.getContentType();
    }

    @Override
    public InputStream getResponseContent() throws Exception {
        return new ByteArrayInputStream(responseOut.toByteArray());
    }

// ------------------------------------------------------------------------

    private void handleListMethods(SoapMessageImpl request) throws Exception {
        log.trace("handleListMethods()");

        MethodListType methodList = OBJECT_FACTORY.createMethodListType();
        methodList.getService().addAll(
                serverConfProvider.getServicesByDescriptionType(request.getService().getClientId(), DescriptionType.WSDL));

        SoapMessageImpl result = createMethodListResponse(request,
                OBJECT_FACTORY.createListMethodsResponse(methodList));
        responseEncoder.soap(result, new HashMap<>());
    }

    private void handleAllowedMethods(SoapMessageImpl request)
            throws Exception {
        log.trace("handleAllowedMethods()");

        MethodListType methodList = OBJECT_FACTORY.createMethodListType();
        methodList.getService().addAll(
                serverConfProvider.getAllowedServicesByDescriptionType(
                        request.getService().getClientId(),
                        request.getClient(), DescriptionType.WSDL));

        SoapMessageImpl result = createMethodListResponse(request,
                OBJECT_FACTORY.createAllowedMethodsResponse(methodList));
        responseEncoder.soap(result, new HashMap<>());
    }

    private void handleGetWsdl(SoapMessageImpl request) throws Exception {
        log.trace("handleGetWsdl()");

        Unmarshaller um = JaxbUtils.createUnmarshaller(WsdlRequestData.class);

        WsdlRequestData requestData = um.unmarshal(
                SoapUtils.getFirstChild(request.getSoap().getSOAPBody()),
                WsdlRequestData.class).getValue();

        if (StringUtils.isBlank(requestData.getServiceCode())) {
            throw new CodedException(X_INVALID_REQUEST,
                    "Missing serviceCode in message body");
        }

        ServiceId serviceId = requestData.toServiceId(request.getService().getClientId());
        String url = getWsdlUrl(serviceId);
        if (url == null) {
            throw new CodedException(X_UNKNOWN_SERVICE,
                    "Could not find wsdl URL for service %s",
                    requestData.toServiceId(
                            request.getService().getClientId()));
        }

        log.info("Downloading WSDL from URL: {}", url);
        try (InputStream in = modifyWsdl(getWsdl(url, serviceId))) {
            Map<String, String> additionalHeaders = new HashMap<>();
            additionalHeaders.put("Content-Transfer-Encoding", "binary");
            additionalHeaders.put("Content-ID", "<wsdl=" + UUID.randomUUID() + "@x-road.eu>");
            responseEncoder.soap(SoapUtils.toResponse(request), new HashMap<>());
            responseEncoder.attachment(MimeTypes.TEXT_XML, in, additionalHeaders);
        }
    }

// ------------------------------------------------------------------------

    private String getWsdlUrl(ServiceId service) throws Exception {
        ServiceDescription wsdl = ServerConfDatabaseCtx.doInTransaction(
                session -> ServiceDescriptionMapper.get().toTarget(
                        new ServiceDescriptionDAOImpl().getServiceDescription(session, service)
                )
        );
        if (wsdl != null && wsdl.getType() != DescriptionType.WSDL) {
            throw new CodedException(X_INVALID_SERVICE_TYPE,
                    "Service is a REST service and does not have a WSDL");
        }
        return wsdl != null ? wsdl.getUrl() : null;
    }

    private static SoapMessageImpl createMethodListResponse(
            SoapMessageImpl requestMessage,
            final JAXBElement<MethodListType> methodList) throws Exception {

        return SoapUtils.toResponse(requestMessage,
                new SOAPCallback() {
                    @Override
                    public void call(SOAPMessage soap) throws Exception {
                        soap.getSOAPBody().removeContents();
                        marshal(methodList, soap.getSOAPBody());
                    }
                });
    }

    private static void marshal(Object object, Node out) throws Exception {
        Marshaller marshaller = JAXB_CTX.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
        marshaller.marshal(object, out);
    }

    private static JAXBContext initJaxbCtx() {
        try {
            return JAXBContext.newInstance(ObjectFactory.class);
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * We need a lexicalHandler for the reader, to catch XML comments.
     * This just delegates to serializer (TransformerHandler) which is
     * a lexicalHandler, too
     */
    private static class CommentsHandler extends DefaultHandler2 {
        private LexicalHandler serializer;

        protected CommentsHandler(LexicalHandler serializer) {
            super();
            this.serializer = serializer;
        }

        @Override
        public void comment(char[] ch, int start, int length) throws SAXException {
            serializer.comment(ch, start, length);
        }
    }

    /**
     * reads a WSDL from input stream, modifies it and returns input stream to the result
     * @param wsdl
     * @return
     */
    private InputStream modifyWsdl(InputStream wsdl) {
        try {
            TransformerHandler serializer = TRANSFORMER_FACTORY.newTransformerHandler();
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);
            serializer.setResult(result);

            OverwriteAttributeFilter filter = getModifyWsdlFilter();
            filter.setContentHandler(serializer);

            XMLReader xmlreader = XmlUtils.createXmlReader();
            xmlreader.setFeature("http://xml.org/sax/features/namespace-prefixes", true);
            xmlreader.setProperty("http://xml.org/sax/properties/lexical-handler",
                    new CommentsHandler(serializer));
            xmlreader.setContentHandler(filter);

            // parse XML, filter it, put end result to a String
            xmlreader.parse(new InputSource(wsdl));
            String resultString = writer.toString();
            log.debug("result of WSDL cleanup: {}", resultString);

            // offer InputStream into processed String
            return new ByteArrayInputStream(resultString.getBytes(StandardCharsets.UTF_8));
        } catch (IOException | SAXException | TransformerConfigurationException | ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    protected OverwriteAttributeFilter getModifyWsdlFilter() {
        return OverwriteAttributeFilter.createOverwriteSoapAddressFilter(WSDL_ENDPOINT_ADDRESS);
    }

    private InputStream getWsdl(String url, ServiceId serviceId)
            throws HttpClientCreator.HttpClientCreatorException, URISyntaxException, IOException {

        HttpClient client = wsdlHttpClientCreator.getHttpClient();

        HttpContext httpContext = new BasicHttpContext();

        // ServerMessageProcessor uses the same method to pass the ServiceId to CustomSSLSocketFactory
        httpContext.setAttribute(ServiceId.class.getName(), serviceId);

        HttpResponse response = client.execute(new HttpGet(new URI(url)), httpContext);

        StatusLine statusLine = response.getStatusLine();

        if (HttpStatus.SC_OK != statusLine.getStatusCode()) {
            throw new RuntimeException("Received HTTP error: "
                    + statusLine.getStatusCode() + " - " + statusLine.getReasonPhrase());
        }

        return response.getEntity().getContent();
    }
}
