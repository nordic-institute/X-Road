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

import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.AttachmentStream;
import ee.ria.xroad.common.message.SaxSoapParserImpl;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapHeader;
import ee.ria.xroad.common.message.SoapMessage;
import ee.ria.xroad.common.message.SoapMessageDecoder;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapUtils;
import ee.ria.xroad.common.util.CachingStream;
import ee.ria.xroad.common.util.HttpSender;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;
import ee.ria.xroad.common.util.TimeUtils;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.soap.SOAPException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.TeeInputStream;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.http.client.HttpClient;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.cert.CertChain;
import org.niis.xroad.globalconf.impl.cert.CertChainFactory;
import org.niis.xroad.globalconf.impl.cert.CertHelper;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.proxy.core.conf.SigningCtx;
import org.niis.xroad.proxy.core.conf.SigningCtxProvider;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.messagelog.MessageLog;
import org.niis.xroad.proxy.core.protocol.Attachment;
import org.niis.xroad.proxy.core.protocol.ProxyMessage;
import org.niis.xroad.proxy.core.protocol.ProxyMessageDecoder;
import org.niis.xroad.proxy.core.protocol.ProxyMessageEncoder;
import org.niis.xroad.proxy.core.util.ClientAuthenticationService;
import org.niis.xroad.proxy.core.util.IdentifierValidator;
import org.niis.xroad.proxy.core.util.MessageProcessorBase;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.model.Client;
import org.niis.xroad.serverconf.model.DescriptionType;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.namespace.QName;
import javax.xml.parsers.ParserConfigurationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static ee.ria.xroad.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.X_SERVICE_FAILED_X;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.ErrorCodes.translateWithPrefix;
import static ee.ria.xroad.common.util.EncoderUtils.encodeBase64;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_HASH_ALGO_ID;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_ORIGINAL_CONTENT_TYPE;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_ORIGINAL_SOAP_ACTION;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_REQUEST_ID;
import static ee.ria.xroad.common.util.TimeUtils.getEpochMillisecond;
import static org.niis.xroad.common.core.exception.ErrorCode.ACCESS_DENIED;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_MESSAGE;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_SECURITY_SERVER;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_SERVICE_TYPE;
import static org.niis.xroad.common.core.exception.ErrorCode.MISSING_SIGNATURE;
import static org.niis.xroad.common.core.exception.ErrorCode.MISSING_SOAP;
import static org.niis.xroad.common.core.exception.ErrorCode.SERVICE_DISABLED;
import static org.niis.xroad.common.core.exception.ErrorCode.SERVICE_MALFORMED_URL;
import static org.niis.xroad.common.core.exception.ErrorCode.SERVICE_MISSING_URL;
import static org.niis.xroad.common.core.exception.ErrorCode.SSL_AUTH_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.UNKNOWN_MEMBER;
import static org.niis.xroad.common.core.exception.ErrorCode.UNKNOWN_SERVICE;

@Slf4j
@ArchUnitSuppressed("NoVanillaExceptions")
public class ServerSoapMessageProcessor extends MessageProcessorBase {

    private final X509Certificate[] clientSslCerts;

    private final List<ServiceHandler> handlers = new ArrayList<>();

    private String originalSoapAction;
    private ProxyMessage requestMessage;
    private ServiceId requestServiceId;
    private SoapMessageImpl responseSoap;
    private SoapFault responseFault;
    private String xRequestId;

    private ProxyMessageDecoder decoder;
    private ProxyMessageEncoder encoder;

    private SigningCtx responseSigningCtx;

    private final OpMonitoringData opMonitoringData;

    private final CertHelper certHelper;
    private final OcspVerifierFactory ocspVerifierFactory;
    private final SigningCtxProvider signingCtxProvider;
    private final String tempFilesPath;

    private final List<Attachment> attachmentCache = new ArrayList<>();

    @SuppressWarnings("checkstyle:ParameterNumber")
    public ServerSoapMessageProcessor(RequestWrapper request, ResponseWrapper response,
                                      ProxyProperties proxyProperties, GlobalConfProvider globalConfProvider,
                                      ServerConfProvider serverConfProvider, ClientAuthenticationService clientAuthenticationService,
                                      SigningCtxProvider signingCtxProvider, OcspVerifierFactory ocspVerifierFactory,
                                      CertHelper certHelper, String tempFilesPath,
                                      HttpClient httpClient, OpMonitoringData opMonitoringData,
                                      ServiceHandlerLoader serviceHandlerLoader) {
        super(request, response, proxyProperties, globalConfProvider, serverConfProvider,
                clientAuthenticationService, httpClient);

        this.clientSslCerts = request.getPeerCertificates().orElse(null);
        this.opMonitoringData = opMonitoringData;

        this.certHelper = certHelper;
        this.ocspVerifierFactory = ocspVerifierFactory;
        this.signingCtxProvider = signingCtxProvider;
        this.tempFilesPath = tempFilesPath;

        loadServiceHandlers(serviceHandlerLoader);
    }

    @Override
    @WithSpan
    public void process() throws Exception {
        log.info("process({})", jRequest.getContentType());

        xRequestId = jRequest.getHeaders().get(HEADER_REQUEST_ID);

        opMonitoringData.setXRequestId(xRequestId);
        opMonitoringDataHelper.updateOpMonitoringClientSecurityServerAddress(opMonitoringData, getClientAuthCert());
        opMonitoringDataHelper.updateOpMonitoringServiceSecurityServerAddress(opMonitoringData);

        try {
            readMessage();

            handleRequest();

            sign();
            logResponseMessage();
            writeSignature();

            close();

            postprocess();
        } catch (Exception ex) {
            handleException(ex);
        } finally {
            if (requestMessage != null) {
                requestMessage.consume();
            }
        }
    }

    @Override
    public boolean verifyMessageExchangeSucceeded() {
        return responseSoap != null && responseFault == null;
    }

    @Override
    protected void preprocess() {
        encoder = new ProxyMessageEncoder(jResponse.getOutputStream(), SoapUtils.getHashAlgoId());

        jResponse.setContentType(encoder.getContentType());
        jResponse.addHeader(HEADER_HASH_ALGO_ID, SoapUtils.getHashAlgoId().name());
    }

    @Override
    protected void postprocess() {
        opMonitoringData.setSucceeded(true);
    }

    private void loadServiceHandlers(ServiceHandlerLoader serviceHandlerLoader) {
        serviceHandlerLoader.loadSoapServiceHandlers().forEach(handler -> {
            handlers.add(handler);
            log.debug("Loaded service handler: {}", handler.getClass().getName());
        });

        handlers.add(new DefaultServiceHandlerImpl(
                serverConfProvider,
                globalConfProvider)); // default handler
    }

    private ServiceHandler getServiceHandler(ProxyMessage request) {
        for (ServiceHandler handler : handlers) {
            if (handler.canHandle(requestServiceId, request)) {
                return handler;
            }
        }

        return null;
    }

    private void handleRequest()
            throws SOAPException, JAXBException, IOException, URISyntaxException,
            ParserConfigurationException, HttpClientCreator.HttpClientCreatorException, SAXException {
        ServiceHandler handler = getServiceHandler(requestMessage);

        if (handler == null) {
            handler = new DefaultServiceHandlerImpl(serverConfProvider, globalConfProvider);
        }

        if (handler.shouldVerifyAccess()) {
            verifyAccess();
        }

        if (handler.shouldVerifySignature()) {
            verifySignature();
        }

        if (handler.shouldLogSignature()) {
            logRequestMessage();
        }

        try {
            handler.startHandling(jRequest, requestMessage, opMonitoringData);
            parseResponse(handler);
        } finally {
            handler.finishHandling();
        }
    }

    private void readMessage() throws Exception {
        log.trace("readMessage()");

        originalSoapAction = SoapUtils.validateSoapActionHeader(jRequest.getHeaders().get(HEADER_ORIGINAL_SOAP_ACTION));
        requestMessage = new ProxyMessage(jRequest.getHeaders().get(HEADER_ORIGINAL_CONTENT_TYPE),
                tempFilesPath) {
            @Override
            public void soap(SoapMessageImpl soapMessage, Map<String, String> additionalHeaders)
                    throws CertificateEncodingException, IOException {
                super.soap(soapMessage, additionalHeaders);

                opMonitoringDataHelper.updateOpMonitoringDataBySoapMessage(opMonitoringData, soapMessage);

                requestServiceId = soapMessage.getService();

                verifySecurityServer();
                verifyClientStatus();

                responseSigningCtx = signingCtxProvider.createSigningCtx(requestServiceId.getClientId());

                if (proxyProperties.sslEnabled()) {
                    verifySslClientCert();
                }
            }
        };

        decoder = new ProxyMessageDecoder(globalConfProvider, ocspVerifierFactory,
                requestMessage, jRequest.getContentType(), false,
                getHashAlgoId(jRequest));
        try {
            decoder.parse(jRequest.getInputStream());
        } catch (XrdRuntimeException e) {
            throw e.withPrefix(X_SERVICE_FAILED_X);
        }

        updateOpMonitoringDataByRequest();

        // Check if the input contained all the required bits.
        checkRequest();
    }

    private void updateOpMonitoringDataByRequest() {
        if (requestMessage.getSoap() != null) {
            opMonitoringData.setRequestAttachmentCount(decoder.getAttachmentCount());

            if (decoder.getAttachmentCount() > 0) {
                opMonitoringData.setRequestMimeSize(requestMessage.getSoap().getBytes().length
                        + decoder.getAttachmentsByteCount());
            }
        }
    }

    private void checkRequest() {
        if (requestMessage.getSoap() == null) {
            throw XrdRuntimeException.systemException(MISSING_SOAP, "Request does not have SOAP message");
        }

        if (requestMessage.getSignature() == null) {
            throw XrdRuntimeException.systemException(MISSING_SIGNATURE, "Request does not have signature");
        }
        IdentifierValidator.checkIdentifier(requestMessage.getSoap().getClient());
        IdentifierValidator.checkIdentifier(requestMessage.getSoap().getService());
        IdentifierValidator.checkIdentifier(requestMessage.getSoap().getSecurityServer());
    }

    private void verifyClientStatus() {
        ClientId client = requestServiceId.getClientId();

        String status = serverConfProvider.getMemberStatus(client);

        if (!Client.STATUS_REGISTERED.equals(status)) {
            throw XrdRuntimeException.systemException(UNKNOWN_MEMBER, "Client '%s' not found".formatted(client));
        }
    }

    private void verifySslClientCert() throws CertificateEncodingException, IOException {
        log.trace("verifySslClientCert()");

        if (requestMessage.getOcspResponses().isEmpty()) {
            throw XrdRuntimeException.systemException(SSL_AUTH_FAILED,
                    "Cannot verify TLS certificate, corresponding OCSP response is missing");
        }

        String instanceIdentifier = requestMessage.getSoap().getClient().getXRoadInstance();

        X509Certificate trustAnchor = globalConfProvider.getCaCert(instanceIdentifier,
                clientSslCerts[clientSslCerts.length - 1]);

        if (trustAnchor == null) {
            throw XrdRuntimeException.systemInternalError("Unable to find trust anchor");
        }

        try {
            CertChain chain = CertChainFactory.create(instanceIdentifier, ArrayUtils.add(clientSslCerts, trustAnchor));
            certHelper.verifyAuthCert(chain, requestMessage.getOcspResponses(), requestMessage.getSoap().getClient());
        } catch (Exception e) {
            throw XrdRuntimeException.systemException(SSL_AUTH_FAILED, e);
        }
    }

    private void verifySecurityServer() {
        final SecurityServerId requestServerId = requestMessage.getSoap().getSecurityServer();

        if (requestServerId != null) {
            final SecurityServerId serverId = serverConfProvider.getIdentifier();

            if (!requestServerId.equals(serverId)) {
                throw XrdRuntimeException.systemException(INVALID_SECURITY_SERVER,
                        "Invalid security server identifier '%s' expected '%s'".formatted(requestServerId, serverId));
            }
        }
    }

    private void verifyAccess() {
        log.trace("verifyAccess()");

        if (!serverConfProvider.serviceExists(requestServiceId)) {
            throw XrdRuntimeException.systemException(UNKNOWN_SERVICE, "Unknown service: %s".formatted(requestServiceId));
        }

        DescriptionType descriptionType = serverConfProvider.getDescriptionType(requestServiceId);
        if (descriptionType != null && descriptionType != DescriptionType.WSDL) {
            throw XrdRuntimeException.systemException(INVALID_SERVICE_TYPE,
                    "Service is a REST service and cannot be called using SOAP interface");
        }

        if (!serverConfProvider.isQueryAllowed(requestMessage.getSoap().getClient(), requestServiceId)) {
            throw XrdRuntimeException.systemException(ACCESS_DENIED, "Request is not allowed: %s".formatted(requestServiceId));
        }

        String disabledNotice = serverConfProvider.getDisabledNotice(requestServiceId);

        if (disabledNotice != null) {
            throw XrdRuntimeException.systemException(SERVICE_DISABLED, "Service %s is disabled: %s".formatted(requestServiceId,
                    disabledNotice));
        }
    }

    private void verifySignature() {
        log.trace("verifySignature()");

        decoder.verify(requestMessage.getSoap().getClient(), requestMessage.getSignature());
    }

    private void logRequestMessage() {
        log.trace("logRequestMessage()");

        MessageLog.log(requestMessage.getSoap(), requestMessage.getSignature(), requestMessage.getAttachments(), false, xRequestId);
    }

    private void logResponseMessage() {
        if (responseSoap != null && encoder != null) {
            log.trace("logResponseMessage()");
            MessageLog.log(responseSoap, encoder.getSignature(), getAttachments(), false, xRequestId);
        }
    }

    private List<AttachmentStream> getAttachments() {
        return attachmentCache.stream().map(Attachment::getAttachmentStream).toList();
    }

    private void sendRequest(String serviceAddress, HttpSender httpSender) {
        log.trace("sendRequest({})", serviceAddress);

        URI uri;
        try {
            uri = new URI(serviceAddress);
        } catch (URISyntaxException e) {
            throw XrdRuntimeException.systemException(SERVICE_MALFORMED_URL, "Malformed service address '%s': %s".formatted(serviceAddress,
                    e.getMessage()));
        }

        log.info("Sending request to {}", uri);
        try {
            opMonitoringData.setRequestOutTs(getEpochMillisecond());
            httpSender.doPost(uri, new ProxyMessageSoapEntity(requestMessage));
            opMonitoringData.setResponseInTs(getEpochMillisecond());
        } catch (Exception ex) {
            if (ex instanceof XrdRuntimeException) {
                opMonitoringData.setResponseInTs(getEpochMillisecond());
            }
            throw translateException(ex).withPrefix(X_SERVICE_FAILED_X);
        }
    }

    private void parseResponse(ServiceHandler handler) {
        log.trace("parseResponse()");

        preprocess();

        // Preserve the original content type of the service response
        jResponse.addHeader(HEADER_ORIGINAL_CONTENT_TYPE, handler.getResponseContentType());

        try (SoapMessageHandler messageHandler = new SoapMessageHandler()) {
            SoapMessageDecoder soapMessageDecoder = new SoapMessageDecoder(handler.getResponseContentType(),
                    messageHandler, new ResponseSoapParserImpl());
            soapMessageDecoder.parse(handler.getResponseContent());
        } catch (Exception ex) {
            throw translateException(ex).withPrefix(X_SERVICE_FAILED_X);
        }

        // If we received a fault from the service, we just send it back
        // to the client.
        if (responseFault != null) {
            throw responseFault.toXrdRuntimeException();
        }

        // If we did not parse a response message (empty response
        // from server?), it is an error instead.
        if (responseSoap == null) {
            throw XrdRuntimeException.systemException(INVALID_MESSAGE, "No response message received from service").withPrefix(
                    X_SERVICE_FAILED_X);
        }

        updateOpMonitoringDataByResponse();
    }

    private void updateOpMonitoringDataByResponse() {
        opMonitoringData.setResponseAttachmentCount(encoder.getAttachmentCount());

        if (encoder.getAttachmentCount() > 0) {
            opMonitoringData.setResponseMimeSize(responseSoap.getBytes().length + encoder.getAttachmentsByteCount());
        }
    }

    private void sign() throws Exception {
        log.trace("sign({})", requestServiceId.getClientId());

        encoder.sign(responseSigningCtx);
    }

    private void writeSignature() throws Exception {
        log.trace("writeSignature()");

        encoder.writeSignature();
    }

    private void close() throws Exception {
        log.trace("close()");

        encoder.close();
    }

    private void handleException(Exception ex) throws Exception {
        if (encoder != null) {
            XrdRuntimeException exception;
            if (ex instanceof XrdRuntimeException xrdEx && xrdEx.hasSoapFault()) {
                exception = xrdEx;
            } else {
                exception = translateWithPrefix(SERVER_SERVERPROXY_X, ex);
            }

            opMonitoringData.setFaultCodeAndString(exception);
            opMonitoringData.setResponseOutTs(getEpochMillisecond(), false);

            encoder.fault(SoapFault.createFaultXml(exception));
            encoder.close();
        } else {
            throw ex;
        }
    }

    private X509Certificate getClientAuthCert() {
        return clientSslCerts != null ? clientSslCerts[0] : null;
    }

    private static DigestAlgorithm getHashAlgoId(RequestWrapper request) {
        String hashAlgoId = request.getHeaders().get(HEADER_HASH_ALGO_ID);

        if (hashAlgoId == null) {
            throw XrdRuntimeException.systemInternalError("Could not get hash algorithm identifier from message");
        }

        return DigestAlgorithm.ofName(hashAlgoId);
    }

    private final class DefaultServiceHandlerImpl extends AbstractServiceHandler {

        private HttpSender sender;

        DefaultServiceHandlerImpl(ServerConfProvider serverConfProvider, GlobalConfProvider globalConfProvider) {
            super(serverConfProvider, globalConfProvider);
        }

        @Override
        public boolean shouldVerifyAccess() {
            return true;
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
        public boolean canHandle(ServiceId requestSrvcId, ProxyMessage requestProxyMessage) {
            return true;
        }

        @Override
        public void startHandling(RequestWrapper request, ProxyMessage proxyRequestMessage,
                                  OpMonitoringData monitoringData) {
            sender = createHttpSender();

            log.trace("processRequest({})", requestServiceId);

            String address = serverConfProvider.getServiceAddress(requestServiceId);

            if (address == null || address.isEmpty()) {
                throw XrdRuntimeException.systemException(SERVICE_MISSING_URL, "Service address not specified for '%s'".formatted(
                        requestServiceId));
            }

            int timeout = TimeUtils.secondsToMillis(serverConfProvider.getServiceTimeout(requestServiceId));

            sender.setConnectionTimeout(timeout);
            sender.setSocketTimeout(timeout);
            sender.setAttribute(ServiceId.class.getName(), requestServiceId);

            sender.addHeader("accept-encoding", "");
            sender.addHeader("SOAPAction", originalSoapAction);
            sendRequest(address, sender);
        }

        @Override
        public void finishHandling() {
            sender.close();
            sender = null;
        }

        @Override
        public String getResponseContentType() {
            return sender.getResponseContentType();
        }

        @Override
        public InputStream getResponseContent() {
            return sender.getResponseContent();
        }
    }

    private final class SoapMessageHandler implements SoapMessageDecoder.Callback {
        @Override
        public void soap(SoapMessage message, Map<String, String> headers) throws UnsupportedEncodingException {
            responseSoap = (SoapMessageImpl) message;

            opMonitoringData.setResponseSize(responseSoap.getBytes().length);
            opMonitoringData.setResponseOutTs(getEpochMillisecond(), true);

            encoder.soap(responseSoap, headers);
        }

        @Override
        public void attachment(String contentType, InputStream content, Map<String, String> additionalHeaders)
                throws IOException {
            CachingStream attachmentCacheStream = new CachingStream(tempFilesPath);
            try (TeeInputStream tis = new TeeInputStream(content, attachmentCacheStream)) {
                encoder.attachment(contentType, tis, additionalHeaders);
                attachmentCache.add(new Attachment(contentType, attachmentCacheStream, additionalHeaders));
            }
        }

        @Override
        public void fault(SoapFault fault) {
            responseFault = fault;
        }

        @Override
        public void onCompleted() {
            // Do nothing.
        }

        @Override
        @ArchUnitSuppressed("NoVanillaExceptions")
        public void onError(Exception t) throws Exception {
            throw t;
        }

        @Override
        public void close() {
            // Do nothing.
        }
    }

    /**
     * Soap parser that adds the request message hash to the response message header.
     */
    private final class ResponseSoapParserImpl extends SaxSoapParserImpl {

        private boolean inHeader;
        private boolean inBody;
        private boolean inExistingRequestHash;
        private boolean bufferFlushed = true;

        private char[] headerElementTabs;

        private char[] bufferedChars;
        private int bufferedOffset;
        private int bufferedLength;

        // force usage of processed XML since we need to write the request hash
        @Override
        protected boolean isProcessedXmlRequired() {
            return true;
        }

        @Override
        protected SoapHeaderHandler getSoapHeaderHandler(SoapHeader header) {
            return new SoapHeaderHandler(header) {
                @Override
                protected void openTag() {
                    super.openTag();
                    inHeader = true;
                }

                @Override
                protected void closeTag() {
                    super.closeTag();
                    inHeader = false;
                }
            };
        }

        @Override
        protected void writeEndElementXml(String prefix, QName element, Attributes attributes, Writer writer) throws IOException {
            if (inHeader && element.equals(QNAME_XROAD_REQUEST_HASH)) {
                inExistingRequestHash = false;
            } else {
                writeBufferedCharacters(writer);
                super.writeEndElementXml(prefix, element, attributes, writer);
            }

            if (inHeader && element.equals(QNAME_XROAD_QUERY_ID)) {
                try {
                    byte[] hashBytes = requestMessage.getSoap().getHash();
                    String hash = encodeBase64(hashBytes);

                    AttributesImpl hashAttrs = new AttributesImpl(attributes);
                    DigestAlgorithm algoUri = SoapUtils.getHashAlgoId();
                    hashAttrs.addAttribute("", "", ATTR_ALGORITHM_ID, "xs:string", algoUri.uri());

                    char[] tabs = headerElementTabs != null ? headerElementTabs : new char[0];
                    super.writeCharactersXml(tabs, 0, tabs.length, writer);
                    super.writeStartElementXml(prefix, QNAME_XROAD_REQUEST_HASH, hashAttrs, writer);
                    super.writeCharactersXml(hash.toCharArray(), 0, hash.length(), writer);
                    super.writeEndElementXml(prefix, QNAME_XROAD_REQUEST_HASH, hashAttrs, writer);
                } catch (Exception e) {
                    throw translateException(e);
                }
            }
        }

        @Override
        protected void writeStartElementXml(String prefix, QName element, Attributes attributes, Writer writer) throws IOException {
            if (inHeader && element.equals(QNAME_XROAD_REQUEST_HASH)) {
                inExistingRequestHash = true;
            } else {
                if (!inBody && element.equals(QNAME_SOAP_BODY)) {
                    inBody = true;
                }

                writeBufferedCharacters(writer);
                super.writeStartElementXml(prefix, element, attributes, writer);
            }
        }

        private void writeBufferedCharacters(Writer writer) throws IOException {
            // Write the characters we ignored at the last characters event
            if (!bufferFlushed) {
                super.writeCharactersXml(bufferedChars, bufferedOffset, bufferedLength, writer);
                bufferFlushed = true;
            }
        }

        @Override
        protected void writeCharactersXml(char[] characters, int start, int length, Writer writer) throws IOException {
            if (inHeader && headerElementTabs == null) {
                String value = new String(characters, start, length);

                if (value.trim().isEmpty()) {
                    headerElementTabs = value.toCharArray();
                }
            }

            // When writing characters outside of the SOAP body, delay this
            // operation until the next event, sometimes we don't want to write
            // these characters, like when we're discarding a header
            if (!inBody && bufferFlushed) {
                bufferCharacters(characters, start, length);
            } else if (!inExistingRequestHash) {
                writeBufferedCharacters(writer);
                super.writeCharactersXml(characters, start, length, writer);
            }
        }

        private void bufferCharacters(char[] characters, int start, int length) {
            if (bufferedChars == null || bufferedChars.length < characters.length) {
                bufferedChars = ArrayUtils.clone(characters);
            } else {
                System.arraycopy(characters, start, bufferedChars, start, length);
            }

            bufferedOffset = start;
            bufferedLength = length;
            bufferFlushed = false;
        }
    }
}
