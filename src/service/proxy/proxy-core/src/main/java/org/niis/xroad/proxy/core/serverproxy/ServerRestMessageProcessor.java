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

import ee.ria.xroad.common.crypto.Digests;
import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.message.RestResponse;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapUtils;
import ee.ria.xroad.common.util.RequestWrapper;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.CommonProperties;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.proxy.core.conf.SigningCtx;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.messagelog.MessageLog;
import org.niis.xroad.proxy.core.protocol.ProxyMessage;
import org.niis.xroad.proxy.core.protocol.ProxyMessageDecoder;
import org.niis.xroad.proxy.core.protocol.ProxyMessageEncoder;
import org.niis.xroad.proxy.core.service.ClientVerificationService;
import org.niis.xroad.proxy.core.service.MessageSigningService;
import org.niis.xroad.proxy.core.util.CachingStream;
import org.niis.xroad.proxy.core.util.IdentifierValidator;
import org.niis.xroad.proxy.core.util.OpMonitoringDataHelper;
import org.niis.xroad.proxy.core.util.RestRequestContext;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.model.DescriptionType;

import java.io.IOException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

import static ee.ria.xroad.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.X_SERVICE_FAILED_X;
import static ee.ria.xroad.common.ErrorCodes.translateWithPrefix;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_HASH_ALGO_ID;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_ORIGINAL_CONTENT_TYPE;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_REQUEST_ID;
import static org.niis.xroad.common.core.exception.ErrorCode.ACCESS_DENIED;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_SERVICE_TYPE;
import static org.niis.xroad.common.core.exception.ErrorCode.MISSING_REST;
import static org.niis.xroad.common.core.exception.ErrorCode.MISSING_SIGNATURE;
import static org.niis.xroad.common.core.exception.ErrorCode.SERVICE_DISABLED;
import static org.niis.xroad.common.core.exception.ErrorCode.UNKNOWN_SERVICE;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
@ArchUnitSuppressed("NoVanillaExceptions")
public class ServerRestMessageProcessor {

    private final MessageSigningService messageSigningService;
    private final ClientVerificationService clientVerificationService;
    private final OpMonitoringDataHelper opMonitoringDataHelper;
    private final GlobalConfProvider globalConfProvider;
    private final ServerConfProvider serverConfProvider;
    private final ProxyProperties proxyProperties;
    private final CommonProperties commonProperties;
    private final OcspVerifierFactory ocspVerifierFactory;
    private final ServiceHandlerLoader serviceHandlerLoader;

    /**
     * Processes a server-side REST request.
     *
     * @param ctx the request context containing request, response and monitoring data
     * @return {@code true} if the exchange succeeded
     * @throws Exception in case of any errors
     */
    @WithSpan
    public boolean process(RestRequestContext ctx) throws Exception {
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

        VerifyingProxyMessage requestMessage = null;
        CachingStream restResponseBody = null;
        RestResponse restResponse = null;
        ProxyMessageEncoder encoder = null;
        boolean succeeded = false;

        try {
            requestMessage = readMessage(jRequest, clientSslCerts, opMonitoringData);
            var serviceId = requestMessage.getRequestServiceId();
            var responseSigningCtx = requestMessage.getResponseSigningCtx();

            // preprocess: create encoder and set response headers
            encoder = new ProxyMessageEncoder(jResponse.getOutputStream(), Digests.DEFAULT_DIGEST_ALGORITHM);
            jResponse.setContentType(encoder.getContentType());
            jResponse.putHeader(HEADER_HASH_ALGO_ID, SoapUtils.getHashAlgoId().name());

            var handleResult = handleRequest(requestMessage, serviceId, jRequest, encoder, opMonitoringData);
            restResponse = handleResult.restResponse();
            restResponseBody = handleResult.restResponseBody();

            sign(encoder, responseSigningCtx, serviceId);
            logResponseMessage(requestMessage, restResponse, encoder, restResponseBody, xRequestId);
            writeSignature(encoder);
            close(encoder);

            // postprocess: update monitoring data
            if (restResponse != null) {
                opMonitoringData.setRestResponseStatusCode(restResponse.getResponseCode());
            }
            succeeded = restResponse != null && !restResponse.isErrorResponse();
        } catch (Exception ex) {
            handleException(ex, encoder, opMonitoringData);
        } finally {
            if (requestMessage != null) {
                requestMessage.consume();
            }
            if (restResponseBody != null) {
                restResponseBody.consume();
            }
        }
        return succeeded;
    }

    private VerifyingProxyMessage readMessage(RequestWrapper jRequest, X509Certificate[] clientSslCerts,
                                              OpMonitoringData opMonitoringData) throws Exception {
        log.trace("readMessage()");

        var requestMessage = new VerifyingProxyMessage(
                jRequest.getHeaders().get(HEADER_ORIGINAL_CONTENT_TYPE),
                commonProperties.tempFilesPath(),
                clientVerificationService, messageSigningService, proxyProperties, clientSslCerts);

        var decoder = new ProxyMessageDecoder(globalConfProvider, ocspVerifierFactory,
                requestMessage, jRequest.getContentType(), false,
                getHashAlgoId(jRequest));
        try {
            decoder.parse(jRequest.getInputStream());
        } catch (XrdRuntimeException e) {
            throw e.withPrefix(X_SERVICE_FAILED_X);
        }

        requestMessage.setDecoder(decoder);

        opMonitoringDataHelper.updateOpMonitoringDataByRestRequest(opMonitoringData, requestMessage.getRest());
        opMonitoringData.setRequestAttachmentCount(0);
        opMonitoringData.setRequestSize(requestMessage.getRest().getMessageBytes().length
                + decoder.getAttachmentsByteCount());

        checkRequest(requestMessage);
        return requestMessage;
    }

    private void checkRequest(VerifyingProxyMessage requestMessage) {
        final RestRequest rest = requestMessage.getRest();
        if (rest == null) {
            throw XrdRuntimeException.systemException(MISSING_REST, "Request does not have REST message");
        }
        if (requestMessage.getSignature() == null) {
            throw XrdRuntimeException.systemException(MISSING_SIGNATURE, "Request does not have signature");
        }

        IdentifierValidator.checkIdentifier(rest.getClientId());
        IdentifierValidator.checkIdentifier(rest.getServiceId());
        IdentifierValidator.checkIdentifier(rest.getTargetSecurityServer());
    }

    private HandleResult handleRequest(VerifyingProxyMessage requestMessage, ServiceId requestServiceId,
                                       RequestWrapper jRequest,
                                       ProxyMessageEncoder encoder, OpMonitoringData opMonitoringData) throws Exception {
        var handler = getServiceHandler(requestMessage, requestServiceId);
        log.trace("handler={}", handler);
        if (handler.shouldVerifyAccess()) {
            verifyAccess(requestServiceId, requestMessage);
        }
        if (handler.shouldVerifySignature()) {
            requestMessage.verifySignature();
        }
        if (handler.shouldLogSignature()) {
            logRequestMessage(requestMessage, jRequest.getHeaders().get(HEADER_REQUEST_ID));
        }
        var result = handler.startHandling(jRequest, requestMessage, encoder, opMonitoringData);
        // No finishHandling() needed — REST handlers have no closeable resources
        return new HandleResult(result.restResponse(), result.restResponseBody());
    }

    private void verifyAccess(ServiceId requestServiceId, VerifyingProxyMessage requestMessage) {
        log.trace("verifyAccess()");

        if (!serverConfProvider.serviceExists(requestServiceId)) {
            throw XrdRuntimeException.systemException(UNKNOWN_SERVICE, "Unknown service: %s".formatted(requestServiceId));
        }

        DescriptionType descriptionType = serverConfProvider.getDescriptionType(requestServiceId);
        if (descriptionType != null && descriptionType != DescriptionType.REST
                && descriptionType != DescriptionType.OPENAPI3) {
            throw XrdRuntimeException.systemException(INVALID_SERVICE_TYPE,
                    "Service is a SOAP service and cannot be called using REST interface");
        }

        if (!serverConfProvider.isQueryAllowed(
                requestMessage.getRest().getClientId(),
                requestServiceId,
                requestMessage.getRest().getVerb().name(),
                requestMessage.getRest().getServicePath())) {
            throw XrdRuntimeException.systemException(ACCESS_DENIED, "Request is not allowed: %s".formatted(requestServiceId));
        }

        String disabledNotice = serverConfProvider.getDisabledNotice(requestServiceId);
        if (disabledNotice != null) {
            throw XrdRuntimeException.systemException(SERVICE_DISABLED, "Service %s is disabled: %s".formatted(requestServiceId,
                    disabledNotice));
        }
    }

    private void logRequestMessage(VerifyingProxyMessage requestMessage, String xRequestId) {
        log.trace("logRequestMessage()");
        MessageLog.log(requestMessage.getRest(), requestMessage.getSignature(), requestMessage.getRestBody(),
                false, xRequestId);
    }

    private void logResponseMessage(VerifyingProxyMessage requestMessage, RestResponse restResponse,
                                    ProxyMessageEncoder encoder, CachingStream restResponseBody, String xRequestId) {
        log.trace("log response message");
        MessageLog.log(requestMessage.getRest(), restResponse, encoder.getSignature(),
                restResponseBody == null ? null : restResponseBody.getCachedContents(), false, xRequestId);
    }

    private void sign(ProxyMessageEncoder encoder, SigningCtx responseSigningCtx, ServiceId requestServiceId) throws Exception {
        log.trace("sign({})", requestServiceId.getClientId());
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
        log.debug("Request failed", ex);

        if (encoder != null) {
            XrdRuntimeException exception;
            if (ex instanceof XrdRuntimeException xrdEx && xrdEx.hasSoapFault()) {
                exception = xrdEx;
            } else {
                exception = translateWithPrefix(SERVER_SERVERPROXY_X, ex);
            }
            opMonitoringData.setFaultCodeAndString(exception);
            encoder.fault(SoapFault.createFaultXml(exception));
            encoder.close();
        } else {
            throw ex;
        }
    }

    private X509Certificate getClientAuthCert(X509Certificate[] clientSslCerts) {
        return clientSslCerts != null ? clientSslCerts[0] : null;
    }

    private RestServiceHandler getServiceHandler(ProxyMessage request, ServiceId requestServiceId) {
        return serviceHandlerLoader.getRestHandlers().stream()
                .filter(h -> h.canHandle(requestServiceId, request))
                .findFirst()
                .orElseThrow(() -> XrdRuntimeException.systemInternalError(
                        "No REST handler found for service: " + requestServiceId));
        // orElseThrow is safe — DefaultRestServiceHandlerImpl always returns true from canHandle()
    }

    private static DigestAlgorithm getHashAlgoId(RequestWrapper request) {
        String hashAlgoId = request.getHeaders().get(HEADER_HASH_ALGO_ID);
        if (hashAlgoId == null) {
            throw XrdRuntimeException.systemInternalError("Could not get hash algorithm identifier from message");
        }
        return DigestAlgorithm.ofName(hashAlgoId);
    }

    private record HandleResult(RestResponse restResponse, CachingStream restResponseBody) {
    }

    /**
     * A {@link ProxyMessage} subclass that performs client verification callbacks during REST message parsing.
     */
    static class VerifyingProxyMessage extends ProxyMessage {
        private final ClientVerificationService clientVerificationService;
        private final MessageSigningService messageSigningService;
        private final ProxyProperties proxyProperties;
        private final X509Certificate[] clientSslCerts;

        @Getter
        private ServiceId requestServiceId;
        @Getter
        private SigningCtx responseSigningCtx;
        @Getter
        @Setter
        private ProxyMessageDecoder decoder;

        VerifyingProxyMessage(String originalContentType, String tempFilesPath,
                              ClientVerificationService clientVerificationService,
                              MessageSigningService messageSigningService,
                              ProxyProperties proxyProperties,
                              X509Certificate[] clientSslCerts) {
            super(originalContentType, tempFilesPath);
            this.clientVerificationService = clientVerificationService;
            this.messageSigningService = messageSigningService;
            this.proxyProperties = proxyProperties;
            this.clientSslCerts = clientSslCerts;
        }

        @Override
        public void rest(RestRequest message) throws CertificateEncodingException, IOException {
            super.rest(message);
            requestServiceId = message.getServiceId();
            clientVerificationService.verifyClientStatus(requestServiceId.getClientId());
            responseSigningCtx = messageSigningService.createSigningCtx(requestServiceId.getClientId());
            if (proxyProperties.sslEnabled()) {
                clientVerificationService.verifySslClientCert(
                        getRest().getClientId().getXRoadInstance(),
                        clientSslCerts, getOcspResponses(),
                        getRest().getClientId());
            }
        }

        /**
         * Verifies the request signature against the client identity and signature from the decoded message.
         */
        public void verifySignature() {
            log.trace("verifySignature()");
            getDecoder().verify(getRest().getClientId(), getSignature());
        }
    }

}
