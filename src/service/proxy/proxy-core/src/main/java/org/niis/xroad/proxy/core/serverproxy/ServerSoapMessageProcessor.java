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
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapMessageDecoder;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapUtils;
import ee.ria.xroad.common.util.HttpSender;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;
import ee.ria.xroad.common.util.TimeUtils;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.soap.SOAPException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.CommonProperties;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.messagelog.MessageLog;
import org.niis.xroad.proxy.core.protocol.ProxyMessage;
import org.niis.xroad.proxy.core.protocol.ProxyMessageDecoder;
import org.niis.xroad.proxy.core.protocol.ProxyMessageEncoder;
import org.niis.xroad.proxy.core.service.ClientVerificationService;
import org.niis.xroad.proxy.core.service.HttpSenderProvider;
import org.niis.xroad.proxy.core.service.MessageSigningService;
import org.niis.xroad.proxy.core.util.IdentifierValidator;
import org.niis.xroad.proxy.core.util.OpMonitoringDataHelper;
import org.niis.xroad.proxy.core.util.ServerSoapRequestContext;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.model.DescriptionType;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ee.ria.xroad.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.X_SERVICE_FAILED_X;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.ErrorCodes.translateWithPrefix;
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
import static org.niis.xroad.common.core.exception.ErrorCode.UNKNOWN_SERVICE;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
@ArchUnitSuppressed("NoVanillaExceptions")
public class ServerSoapMessageProcessor {

    private final MessageSigningService messageSigningService;
    private final HttpSenderProvider httpSenderProvider;
    private final ClientVerificationService clientVerificationService;
    private final OpMonitoringDataHelper opMonitoringDataHelper;
    private final GlobalConfProvider globalConfProvider;
    private final ServerConfProvider serverConfProvider;
    private final ProxyProperties proxyProperties;
    private final CommonProperties commonProperties;
    private final OcspVerifierFactory ocspVerifierFactory;
    private final ServiceHandlerLoader serviceHandlerLoader;

    /**
     * Creates a fresh set of service handlers for one request.
     * Service handlers (e.g. MetadataServiceHandlerImpl) carry per-request mutable state
     * (responseOut, requestMessage, responseEncoder) and therefore must NOT be shared across requests.
     */
    private List<ServiceHandler> createServiceHandlers() {
        var handlers = new ArrayList<ServiceHandler>();
        serviceHandlerLoader.loadSoapServiceHandlers().forEach(handler -> {
            handlers.add(handler);
            log.trace("Using service handler: {}", handler.getClass().getName());
        });
        handlers.add(new DefaultServiceHandlerImpl(serverConfProvider, globalConfProvider));
        return handlers;
    }

    /**
     * Processes a server-side SOAP request.
     *
     * @param ctx the request context containing request, response and monitoring data
     * @return {@code true} if the exchange succeeded
     * @throws Exception in case of any errors
     */
    @WithSpan
    public boolean process(ServerSoapRequestContext ctx) throws Exception {
        globalConfProvider.verifyValidity();

        var jRequest = ctx.request();
        var jResponse = ctx.response();
        var opMonitoringData = ctx.opMonitoringData();

        log.info("process({})", jRequest.getContentType());

        var xRequestId = jRequest.getHeaders().get(HEADER_REQUEST_ID);
        var clientSslCerts = jRequest.getPeerCertificates().orElse(null);

        opMonitoringData.setXRequestId(xRequestId);
        opMonitoringDataHelper.updateOpMonitoringClientSecurityServerAddress(opMonitoringData, getClientAuthCert(clientSslCerts));
        opMonitoringDataHelper.updateOpMonitoringServiceSecurityServerAddress(opMonitoringData);

        ProxyMessage requestMessage = null;
        // Encoder holder — gets set inside parseResponse(), before any post-response exception can be thrown.
        // Remains null if the exception occurs before parseResponse() is entered (request decode, access check, etc.),
        // in which case handleException() rethrows and ServerProxyHandler sends a clean text/xml fault.
        var encoderHolder = new ProxyMessageEncoder[1];
        boolean succeeded = false;

        try {
            requestMessage = readMessage(jRequest, clientSslCerts, opMonitoringData);

            var handleResult = handleRequest(requestMessage, jRequest, jResponse, opMonitoringData, xRequestId,
                    encoderHolder);

            sign(encoderHolder[0], requestMessage.getSoap().getService().getClientId());
            logResponseMessage(handleResult.responseDecoder(), encoderHolder[0], xRequestId);
            writeSignature(encoderHolder[0]);
            close(encoderHolder[0]);

            succeeded = true;
        } catch (Exception ex) {
            handleException(ex, encoderHolder[0], opMonitoringData);
        } finally {
            if (requestMessage != null) {
                requestMessage.consume();
            }
        }
        return succeeded;
    }

    private record HandleResult(ServerSoapRequestDecoder responseDecoder) {
    }

    private ProxyMessage readMessage(RequestWrapper jRequest, X509Certificate[] clientSslCerts,
                                     OpMonitoringData opMonitoringData) throws Exception {
        log.trace("readMessage()");

        var originalSoapAction = SoapUtils.validateSoapActionHeader(jRequest.getHeaders().get(HEADER_ORIGINAL_SOAP_ACTION));
        var requestMessage = new VerifyingProxyMessage(jRequest.getHeaders().get(HEADER_ORIGINAL_CONTENT_TYPE),
                commonProperties.tempFilesPath(), clientSslCerts, opMonitoringData, originalSoapAction);

        var decoder = new ProxyMessageDecoder(globalConfProvider, ocspVerifierFactory,
                requestMessage, jRequest.getContentType(), false,
                getHashAlgoId(jRequest));
        try {
            decoder.parse(jRequest.getInputStream());
        } catch (XrdRuntimeException e) {
            throw e.withPrefix(X_SERVICE_FAILED_X);
        }

        updateOpMonitoringDataByRequest(requestMessage, decoder, opMonitoringData);
        checkRequest(requestMessage);

        requestMessage.setDecoder(decoder);
        return requestMessage;
    }

    private void updateOpMonitoringDataByRequest(ProxyMessage requestMessage, ProxyMessageDecoder decoder,
                                                 OpMonitoringData opMonitoringData) {
        if (requestMessage.getSoap() != null) {
            opMonitoringData.setRequestAttachmentCount(decoder.getAttachmentCount());

            if (decoder.getAttachmentCount() > 0) {
                opMonitoringData.setRequestMimeSize(requestMessage.getSoap().getBytes().length
                        + decoder.getAttachmentsByteCount());
            }
        }
    }

    private void checkRequest(ProxyMessage requestMessage) {
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

    private HandleResult handleRequest(ProxyMessage requestMessage, RequestWrapper jRequest, ResponseWrapper jResponse,
                                       OpMonitoringData opMonitoringData, String xRequestId,
                                       ProxyMessageEncoder[] encoderHolder)
            throws SOAPException, JAXBException, IOException, URISyntaxException,
            ParserConfigurationException, HttpClientCreator.HttpClientCreatorException, SAXException {
        var requestServiceId = requestMessage.getSoap().getService();

        // Create fresh handler instances per request — service handlers carry mutable per-request state.
        var requestHandlers = createServiceHandlers();
        ServiceHandler handler = getServiceHandler(requestMessage, requestHandlers)
                .orElseGet(() -> new DefaultServiceHandlerImpl(serverConfProvider, globalConfProvider));

        if (handler.shouldVerifyAccess()) {
            verifyAccess(requestMessage, requestServiceId);
        }

        if (handler.shouldVerifySignature()) {
            verifySignature(requestMessage);
        }

        if (handler.shouldLogSignature()) {
            logRequestMessage(requestMessage, xRequestId);
        }

        try {
            handler.startHandling(jRequest, requestMessage, opMonitoringData);
            var responseDecoder = parseResponse(handler, jResponse, requestMessage, opMonitoringData, encoderHolder);
            return new HandleResult(responseDecoder);
        } finally {
            handler.finishHandling();
        }
    }

    private Optional<ServiceHandler> getServiceHandler(ProxyMessage request, List<ServiceHandler> handlers) {
        var requestServiceId = request.getSoap().getService();
        return handlers.stream()
                .filter(h -> h.canHandle(requestServiceId, request))
                .findFirst();
    }

    private void verifySecurityServer(ProxyMessage requestMessage) {
        final SecurityServerId requestServerId = requestMessage.getSoap().getSecurityServer();

        if (requestServerId != null) {
            final SecurityServerId serverId = serverConfProvider.getIdentifier();

            if (!requestServerId.equals(serverId)) {
                throw XrdRuntimeException.systemException(INVALID_SECURITY_SERVER,
                        "Invalid security server identifier '%s' expected '%s'".formatted(requestServerId, serverId));
            }
        }
    }

    private void verifyAccess(ProxyMessage requestMessage, ServiceId requestServiceId) {
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

    private void verifySignature(ProxyMessage requestMessage) {
        log.trace("verifySignature()");
        ((VerifyingProxyMessage) requestMessage).verify();
    }

    private void logRequestMessage(ProxyMessage requestMessage, String xRequestId) {
        log.trace("logRequestMessage()");
        MessageLog.log(requestMessage.getSoap(), requestMessage.getSignature(), requestMessage.getAttachments(), false, xRequestId);
    }

    private void logResponseMessage(ServerSoapRequestDecoder responseDecoder, ProxyMessageEncoder encoder, String xRequestId) {
        if (responseDecoder.getResponseSoap() != null && encoder != null) {
            log.trace("logResponseMessage()");
            MessageLog.log(responseDecoder.getResponseSoap(), encoder.getSignature(), responseDecoder.getAttachmentStreams(),
                    false, xRequestId);
        }
    }

    private void sendRequest(String serviceAddress, HttpSender httpSender, ProxyMessage requestMessage,
                             OpMonitoringData opMonitoringData) {
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

    private ServerSoapRequestDecoder parseResponse(ServiceHandler handler, ResponseWrapper jResponse,
                                                   ProxyMessage requestMessage, OpMonitoringData opMonitoringData,
                                                   ProxyMessageEncoder[] encoderHolder) {
        log.trace("parseResponse()");

        // Preserve the original content type of the service response
        var responseContentType = handler.getResponseContentType();

        // Create the encoder here, mirroring the original preprocess() call timing.
        // Encoder is created only after the backend service has responded, so errors
        // before this point (request validation, access check, send-request failures)
        // will rethrow and be handled by ServerProxyHandler as clean text/xml faults.
        var encoder = new ProxyMessageEncoder(jResponse.getOutputStream(), SoapUtils.getHashAlgoId());
        jResponse.setContentType(encoder.getContentType());
        jResponse.addHeader(HEADER_HASH_ALGO_ID, SoapUtils.getHashAlgoId().name());
        jResponse.addHeader(HEADER_ORIGINAL_CONTENT_TYPE, responseContentType);

        // Store encoder in holder so that handleException() can use it even if this method throws.
        encoderHolder[0] = encoder;

        var responseDecoder = new ServerSoapRequestDecoder(opMonitoringData,
                commonProperties.tempFilesPath(), encoder);
        try {
            var soapMessageDecoder = new SoapMessageDecoder(responseContentType, responseDecoder,
                    new ResponseStaxSoapParserImpl(requestMessage));
            soapMessageDecoder.parse(handler.getResponseContent());
        } catch (Exception ex) {
            throw translateException(ex).withPrefix(X_SERVICE_FAILED_X);
        }

        // If we received a fault from the service, we just send it back to the client.
        if (responseDecoder.getResponseFault() != null) {
            throw responseDecoder.getResponseFault().toXrdRuntimeException();
        }

        // If we did not parse a response message (empty response from server?), it is an error instead.
        if (responseDecoder.getResponseSoap() == null) {
            throw XrdRuntimeException.systemException(INVALID_MESSAGE, "No response message received from service").withPrefix(
                    X_SERVICE_FAILED_X);
        }

        responseDecoder.updateOpMonitoringDataByResponse();

        return responseDecoder;
    }

    private void sign(ProxyMessageEncoder encoder, ClientId clientId) throws Exception {
        log.trace("sign({})", clientId);

        var responseSigningCtx = messageSigningService.createSigningCtx(clientId);
        encoder.sign(responseSigningCtx);
    }

    private void writeSignature(ProxyMessageEncoder encoder) throws Exception {
        log.trace("writeSignature()");
        encoder.writeSignature();
    }

    private void close(ProxyMessageEncoder encoder) throws Exception {
        log.trace("close()");
        encoder.close();
    }

    private void handleException(Exception ex, ProxyMessageEncoder encoder, OpMonitoringData opMonitoringData) throws Exception {
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

    private X509Certificate getClientAuthCert(X509Certificate[] clientSslCerts) {
        return clientSslCerts != null ? clientSslCerts[0] : null;
    }

    private static DigestAlgorithm getHashAlgoId(RequestWrapper request) {
        String hashAlgoId = request.getHeaders().get(HEADER_HASH_ALGO_ID);

        if (hashAlgoId == null) {
            throw XrdRuntimeException.systemInternalError("Could not get hash algorithm identifier from message");
        }

        return DigestAlgorithm.ofName(hashAlgoId);
    }

    /**
     * A {@link ProxyMessage} subclass that performs server verification callbacks during SOAP message parsing
     * and exposes the decoder for signature verification.
     */
    private final class VerifyingProxyMessage extends ProxyMessage {
        private final X509Certificate[] clientSslCerts;
        private final OpMonitoringData opMonitoringData;
        private final String originalSoapAction;

        private ProxyMessageDecoder decoder;

        VerifyingProxyMessage(String originalContentType, String tempFilesPath,
                              X509Certificate[] clientSslCerts, OpMonitoringData opMonitoringData,
                              String originalSoapAction) {
            super(originalContentType, tempFilesPath);
            this.clientSslCerts = clientSslCerts;
            this.opMonitoringData = opMonitoringData;
            this.originalSoapAction = originalSoapAction;
        }

        @Override
        public void soap(SoapMessageImpl soapMessage, Map<String, String> additionalHeaders)
                throws java.security.cert.CertificateEncodingException, IOException {
            super.soap(soapMessage, additionalHeaders);

            opMonitoringDataHelper.updateOpMonitoringDataBySoapMessage(opMonitoringData, soapMessage);

            var requestServiceId = soapMessage.getService();

            verifySecurityServer(this);
            clientVerificationService.verifyClientStatus(requestServiceId.getClientId());

            if (proxyProperties.sslEnabled()) {
                clientVerificationService.verifySslClientCert(
                        getSoap().getClient().getXRoadInstance(),
                        clientSslCerts, getOcspResponses(),
                        getSoap().getClient());
            }
        }

        void verify() {
            decoder.verify(getSoap().getClient(), getSignature());
        }

        void setDecoder(ProxyMessageDecoder decoder) {
            this.decoder = decoder;
        }

        String getOriginalSoapAction() {
            return originalSoapAction;
        }
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
            sender = httpSenderProvider.createServerHttpSender();

            var requestServiceId = proxyRequestMessage.getSoap().getService();
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
            sender.addHeader("SOAPAction", ((VerifyingProxyMessage) proxyRequestMessage).getOriginalSoapAction());
            sendRequest(address, sender, proxyRequestMessage, monitoringData);
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
}
