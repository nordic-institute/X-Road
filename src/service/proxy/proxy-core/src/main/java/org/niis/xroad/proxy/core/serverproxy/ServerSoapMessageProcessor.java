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
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.soap.SOAPException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.messagelog.MessageLog;
import org.niis.xroad.proxy.core.protocol.ProxyMessage;
import org.niis.xroad.proxy.core.protocol.ProxyMessageDecoder;
import org.niis.xroad.proxy.core.protocol.ProxyMessageEncoder;
import org.niis.xroad.proxy.core.service.ClientVerificationService;
import org.niis.xroad.proxy.core.service.MessageSigningService;
import org.niis.xroad.proxy.core.util.IdentifierValidationService;
import org.niis.xroad.proxy.core.util.OpMonitoringDataHelper;
import org.niis.xroad.proxy.core.util.ServerSoapRequestContext;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.model.DescriptionType;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.cert.X509Certificate;
import java.util.Map;

import static ee.ria.xroad.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.X_SERVICE_FAILED_X;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.ErrorCodes.translateWithPrefix;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_HASH_ALGO_ID;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_ORIGINAL_CONTENT_TYPE;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_REQUEST_ID;
import static ee.ria.xroad.common.util.TimeUtils.getEpochMillisecond;
import static org.niis.xroad.common.core.exception.ErrorCode.ACCESS_DENIED;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_MESSAGE;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_SECURITY_SERVER;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_SERVICE_TYPE;
import static org.niis.xroad.common.core.exception.ErrorCode.MISSING_SIGNATURE;
import static org.niis.xroad.common.core.exception.ErrorCode.MISSING_SOAP;
import static org.niis.xroad.common.core.exception.ErrorCode.SERVICE_DISABLED;
import static org.niis.xroad.common.core.exception.ErrorCode.UNKNOWN_SERVICE;
import static org.niis.xroad.common.properties.config.keys.CommonConfigKeys.TEMP_FILES_PATH;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
@ArchUnitSuppressed("NoVanillaExceptions")
public class ServerSoapMessageProcessor {

    private final MessageSigningService messageSigningService;
    private final ClientVerificationService clientVerificationService;
    private final OpMonitoringDataHelper opMonitoringDataHelper;
    private final GlobalConfProvider globalConfProvider;
    private final ServerConfProvider serverConfProvider;
    private final ProxyProperties proxyProperties;
    private final XRoadConfig xRoadConfig;
    private final OcspVerifierFactory ocspVerifierFactory;
    private final ServiceHandlerLoader serviceHandlerLoader;
    private final IdentifierValidationService identifierValidationService;

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
        // Declared before try so handleException() in the catch block can use it.
        // Remains null until parseResponse() creates the encoder after the backend responds.
        ProxyMessageEncoder encoder = null;
        boolean succeeded = false;

        try {
            requestMessage = readMessage(jRequest, clientSslCerts, opMonitoringData);

            try (var handlerResult = prepareHandler(requestMessage, jRequest, opMonitoringData, xRequestId)) {
                // Encoder is created before decoding begins — available to the catch block
                // if decodeResponse() throws partway through (e.g. invalid attachment content).
                encoder = createEncoder(handlerResult, jResponse);
                var responseDecoder = decodeResponse(handlerResult, encoder, requestMessage, opMonitoringData);

                sign(encoder, requestMessage.getSoap().getService().getClientId());
                logResponseMessage(responseDecoder, encoder, xRequestId);
                writeSignature(encoder);
                close(encoder);
            }

            succeeded = true;
        } catch (Exception ex) {
            handleException(ex, encoder, opMonitoringData);
        } finally {
            if (requestMessage != null) {
                requestMessage.consume();
            }
        }
        return succeeded;
    }

    private ProxyMessage readMessage(RequestWrapper jRequest, X509Certificate[] clientSslCerts,
                                     OpMonitoringData opMonitoringData) throws Exception {
        log.trace("readMessage()");

        var requestMessage = new VerifyingProxyMessage(jRequest.getHeaders().get(HEADER_ORIGINAL_CONTENT_TYPE),
                xRoadConfig.value(TEMP_FILES_PATH), clientSslCerts, opMonitoringData);

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
        identifierValidationService.checkIdentifier(requestMessage.getSoap().getClient());
        identifierValidationService.checkIdentifier(requestMessage.getSoap().getService());
        identifierValidationService.checkIdentifier(requestMessage.getSoap().getSecurityServer());
    }

    private ServiceHandlerResult prepareHandler(ProxyMessage requestMessage, RequestWrapper jRequest,
                                                OpMonitoringData opMonitoringData, String xRequestId)
            throws SOAPException, JAXBException, IOException, URISyntaxException,
            ParserConfigurationException, HttpClientCreator.HttpClientCreatorException, SAXException {
        var requestServiceId = requestMessage.getSoap().getService();

        ServiceHandler handler = serviceHandlerLoader.getSoapHandlers().stream()
                .filter(h -> h.canHandle(requestServiceId, requestMessage))
                .findFirst()
                .orElseThrow(() -> XrdRuntimeException.systemInternalError(
                        "No handler found for service: " + requestServiceId));
        // orElseThrow is safe — DefaultServiceHandlerImpl always returns true from canHandle()

        if (handler.shouldVerifyAccess(requestMessage)) {
            verifyAccess(requestMessage, requestServiceId);
        }

        if (handler.shouldVerifySignature()) {
            verifySignature(requestMessage);
        }

        if (handler.shouldLogSignature()) {
            logRequestMessage(requestMessage, xRequestId);
        }

        return handler.startHandling(jRequest, requestMessage, opMonitoringData);
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

    /**
     * Creates and configures the response encoder. Called before {@link #decodeResponse} so that
     * the encoder is available to the catch block even if decoding throws partway through.
     * Encoder creation cannot fail — it only allocates the encoder and writes response headers.
     */
    private ProxyMessageEncoder createEncoder(ServiceHandlerResult handlerResult, ResponseWrapper jResponse) {
        var encoder = new ProxyMessageEncoder(jResponse.getOutputStream(), SoapUtils.getHashAlgoId());
        jResponse.setContentType(encoder.getContentType());
        jResponse.addHeader(HEADER_HASH_ALGO_ID, SoapUtils.getHashAlgoId().name());
        jResponse.addHeader(HEADER_ORIGINAL_CONTENT_TYPE, handlerResult.responseContentType());
        return encoder;
    }

    private ServerSoapRequestDecoder decodeResponse(ServiceHandlerResult handlerResult, ProxyMessageEncoder encoder,
                                                    ProxyMessage requestMessage, OpMonitoringData opMonitoringData) {
        log.trace("decodeResponse()");

        var responseContentType = handlerResult.responseContentType();
        var responseDecoder = new ServerSoapRequestDecoder(opMonitoringData,
                xRoadConfig.value(TEMP_FILES_PATH), encoder);
        try {
            var soapMessageDecoder = new SoapMessageDecoder(responseContentType, responseDecoder,
                    new ResponseStaxSoapParserImpl(requestMessage));
            soapMessageDecoder.parse(handlerResult.responseContent());
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

        private ProxyMessageDecoder decoder;

        VerifyingProxyMessage(String originalContentType, String tempFilesPath,
                              X509Certificate[] clientSslCerts, OpMonitoringData opMonitoringData) {
            super(originalContentType, tempFilesPath);
            this.clientSslCerts = clientSslCerts;
            this.opMonitoringData = opMonitoringData;
        }

        @Override
        public void soap(SoapMessageImpl soapMessage, Map<String, String> additionalHeaders)
                throws java.security.cert.CertificateEncodingException, IOException {
            super.soap(soapMessage, additionalHeaders);

            opMonitoringDataHelper.updateOpMonitoringDataBySoapMessage(opMonitoringData, soapMessage);

            var requestServiceId = soapMessage.getService();

            verifySecurityServer();
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

        private void verifySecurityServer() {
            final SecurityServerId requestServerId = getSoap().getSecurityServer();

            if (requestServerId != null) {
                final SecurityServerId serverId = serverConfProvider.getIdentifier();

                if (!requestServerId.equals(serverId)) {
                    throw XrdRuntimeException.systemException(INVALID_SECURITY_SERVER,
                            "Invalid security server identifier '%s' expected '%s'".formatted(requestServerId, serverId));
                }
            }
        }
    }

}
