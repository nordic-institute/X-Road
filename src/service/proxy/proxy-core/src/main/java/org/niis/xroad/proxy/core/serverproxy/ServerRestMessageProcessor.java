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
import ee.ria.xroad.common.util.TimeUtils;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.TeeInputStream;
import org.apache.http.Header;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpTrace;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.bouncycastle.operator.DigestCalculator;
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
import java.io.OutputStream;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static ee.ria.xroad.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.X_SERVICE_FAILED_X;
import static ee.ria.xroad.common.ErrorCodes.translateWithPrefix;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_HASH_ALGO_ID;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_ORIGINAL_CONTENT_TYPE;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_REQUEST_ID;
import static ee.ria.xroad.common.util.TimeUtils.getEpochMillisecond;
import static org.niis.xroad.common.core.exception.ErrorCode.ACCESS_DENIED;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_SERVICE_TYPE;
import static org.niis.xroad.common.core.exception.ErrorCode.MISSING_REST;
import static org.niis.xroad.common.core.exception.ErrorCode.MISSING_SIGNATURE;
import static org.niis.xroad.common.core.exception.ErrorCode.SERVICE_DISABLED;
import static org.niis.xroad.common.core.exception.ErrorCode.SERVICE_MISSING_URL;
import static org.niis.xroad.common.core.exception.ErrorCode.UNKNOWN_SERVICE;
import static org.niis.xroad.proxy.core.configuration.ServerProxyConfig.SERVER_PROXY_HTTP_CLIENT;

@Slf4j
@ApplicationScoped
@ArchUnitSuppressed("NoVanillaExceptions")
public class ServerRestMessageProcessor {

    private final MessageSigningService messageSigningService;
    private final HttpClient serverHttpClient;
    private final ClientVerificationService clientVerificationService;
    private final OpMonitoringDataHelper opMonitoringDataHelper;
    private final GlobalConfProvider globalConfProvider;
    private final ServerConfProvider serverConfProvider;
    private final ProxyProperties proxyProperties;
    private final CommonProperties commonProperties;
    private final OcspVerifierFactory ocspVerifierFactory;
    private final ServiceHandlerLoader serviceHandlerLoader;

    private List<RestServiceHandler> handlers;

    public ServerRestMessageProcessor(MessageSigningService messageSigningService,
                                      @Named(SERVER_PROXY_HTTP_CLIENT) HttpClient serverHttpClient,
                                      ClientVerificationService clientVerificationService,
                                      OpMonitoringDataHelper opMonitoringDataHelper,
                                      GlobalConfProvider globalConfProvider,
                                      ServerConfProvider serverConfProvider,
                                      ProxyProperties proxyProperties,
                                      CommonProperties commonProperties,
                                      OcspVerifierFactory ocspVerifierFactory,
                                      ServiceHandlerLoader serviceHandlerLoader) {
        this.messageSigningService = messageSigningService;
        this.serverHttpClient = serverHttpClient;
        this.clientVerificationService = clientVerificationService;
        this.opMonitoringDataHelper = opMonitoringDataHelper;
        this.globalConfProvider = globalConfProvider;
        this.serverConfProvider = serverConfProvider;
        this.proxyProperties = proxyProperties;
        this.commonProperties = commonProperties;
        this.ocspVerifierFactory = ocspVerifierFactory;
        this.serviceHandlerLoader = serviceHandlerLoader;
    }

    /**
     * Initialises the REST service handler list once at startup.
     */
    @PostConstruct
    public void init() {
        handlers = new ArrayList<>();
        serviceHandlerLoader.loadRestServiceHandlers().forEach(handler -> {
            handlers.add(handler);
            log.trace("Loaded rest service handler: {}", handler.getClass().getName());
        });
    }

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
        try {
            handler.startHandling(jRequest, requestMessage, encoder, opMonitoringData);
        } finally {
            handler.finishHandling();
        }
        return new HandleResult(handler.getRestResponse(), handler.getRestResponseBody());
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

    private RestServiceHandler getServiceHandler(VerifyingProxyMessage request, ServiceId requestServiceId) {
        for (RestServiceHandler handler : handlers) {
            if (handler.canHandle(requestServiceId, request)) {
                return handler;
            }
        }
        return new DefaultRestServiceHandlerImpl(serverConfProvider, serverHttpClient, commonProperties.tempFilesPath(), request);
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

        public void verifySignature() {
            log.trace("verifySignature()");
            getDecoder().verify(getRest().getClientId(), getSignature());
        }
    }

    @RequiredArgsConstructor
    private static final class DefaultRestServiceHandlerImpl implements RestServiceHandler {
        private final ServerConfProvider serverConfProvider;
        private final HttpClient serverHttpClient;
        private final String tempFilesPath;
        private final VerifyingProxyMessage proxyMessage;

        private RestResponse restResponse;
        private CachingStream restResponseBody;

        private String concatPath(String address, String path) {
            if (path == null || path.isEmpty()) return address;
            if (address.endsWith("/") && path.startsWith("/")) {
                return address.concat(path.substring(1));
            }
            return address.concat(path);
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
        @ArchUnitSuppressed("NoVanillaExceptions")
        public void startHandling(RequestWrapper request, ProxyMessage requestProxyMessage,
                                  ProxyMessageEncoder messageEncoder,
                                  OpMonitoringData monitoringData) throws IOException {
            String address = serverConfProvider.getServiceAddress(requestProxyMessage.getRest().getServiceId());
            if (address == null || address.isEmpty()) {
                throw XrdRuntimeException.systemException(SERVICE_MISSING_URL, "Service address not specified for '%s'".formatted(
                        requestProxyMessage.getRest().getServiceId()));
            }

            address = concatPath(address, requestProxyMessage.getRest().getServicePath());
            final String query = requestProxyMessage.getRest().getQuery();
            if (query != null) {
                address += "?" + query;
            }

            HttpRequestBase req = switch (requestProxyMessage.getRest().getVerb()) {
                case GET -> new HttpGet(address);
                case POST -> new HttpPost(address);
                case PUT -> new HttpPut(address);
                case DELETE -> new HttpDelete(address);
                case PATCH -> new HttpPatch(address);
                case OPTIONS -> new HttpOptions(address);
                case HEAD -> new HttpHead(address);
                case TRACE -> new HttpTrace(address);
            };

            int timeout = TimeUtils.secondsToMillis(serverConfProvider
                    .getServiceTimeout(requestProxyMessage.getRest().getServiceId()));
            req.setConfig(RequestConfig
                    .custom()
                    .setSocketTimeout(timeout)
                    .build());

            for (Header header : requestProxyMessage.getRest().getHeaders()) {
                req.addHeader(header);
            }

            if (req instanceof HttpEntityEnclosingRequest httpEntityEnclosingRequest && requestProxyMessage.hasRestBody()) {
                httpEntityEnclosingRequest.setEntity(new InputStreamEntity(requestProxyMessage.getRestBody(),
                        requestProxyMessage.getRestBody().size()));
            }

            final HttpContext ctx = new BasicHttpContext();
            ctx.setAttribute(ServiceId.class.getName(), requestProxyMessage.getRest().getServiceId());
            monitoringData.setRequestOutTs(getEpochMillisecond());
            final HttpResponse response = serverHttpClient.execute(req, ctx);
            monitoringData.setResponseInTs(getEpochMillisecond());
            final StatusLine statusLine = response.getStatusLine();

            //calculate request hash
            byte[] requestDigest;
            var messageDecoder = proxyMessage.getDecoder();
            if (messageDecoder.getRestBodyDigest() != null) {
                final DigestCalculator dc = Digests.createDigestCalculator(Digests.DEFAULT_DIGEST_ALGORITHM);
                try (OutputStream out = dc.getOutputStream()) {
                    out.write(requestProxyMessage.getRest().getHash());
                    out.write(messageDecoder.getRestBodyDigest());
                }
                requestDigest = dc.getDigest();
            } else {
                requestDigest = requestProxyMessage.getRest().getHash();
            }

            restResponse = new RestResponse(requestProxyMessage.getRest().getClientId(),
                    requestProxyMessage.getRest().getQueryId(),
                    requestDigest,
                    requestProxyMessage.getRest().getServiceId(),
                    statusLine.getStatusCode(),
                    statusLine.getReasonPhrase(),
                    Arrays.asList(response.getAllHeaders()),
                    request.getHeaders().get(HEADER_REQUEST_ID)
            );
            messageEncoder.restResponse(restResponse);

            if (response.getEntity() != null) {
                restResponseBody = new CachingStream(tempFilesPath);
                TeeInputStream tee = new TeeInputStream(response.getEntity().getContent(), restResponseBody);
                messageEncoder.restBody(tee);
                EntityUtils.consume(response.getEntity());
            }

            monitoringData.setResponseAttachmentCount(0);
            monitoringData.setResponseSize(restResponse.getMessageBytes().length
                    + messageEncoder.getAttachmentsByteCount());
        }

        @Override
        public RestResponse getRestResponse() {
            return restResponse;
        }

        @Override
        public CachingStream getRestResponseBody() {
            return restResponseBody;
        }

        @Override
        public void finishHandling() {
            // NOP
        }
    }
}
