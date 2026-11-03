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
package org.niis.xroad.proxy.core.clientproxy;

import ee.ria.xroad.common.crypto.Digests;
import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.message.RestResponse;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.util.HttpSender;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.message.BasicHeader;
import org.apache.james.mime4j.MimeException;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.util.io.TeeInputStream;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;
import org.niis.xroad.common.core.exception.ClientFacingErrorPolicy;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.CommonProperties;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.cert.CertChain;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.dsp.AssetAccessResponse;
import org.niis.xroad.proxy.core.dsp.DspRequest;
import org.niis.xroad.proxy.core.dsp.DspRequestProcessor;
import org.niis.xroad.proxy.core.messagelog.MessageLog;
import org.niis.xroad.proxy.core.protocol.ProxyMessage;
import org.niis.xroad.proxy.core.protocol.ProxyMessageDecoder;
import org.niis.xroad.proxy.core.protocol.ProxyMessageEncoder;
import org.niis.xroad.proxy.core.service.ClientVerificationService;
import org.niis.xroad.proxy.core.service.HttpSenderProvider;
import org.niis.xroad.proxy.core.service.LegacyCallRefusalPolicy;
import org.niis.xroad.proxy.core.service.MessageSigningService;
import org.niis.xroad.proxy.core.util.CachingStream;
import org.niis.xroad.proxy.core.util.IdentifierValidationService;
import org.niis.xroad.proxy.core.util.OpMonitoringDataHelper;
import org.niis.xroad.proxy.core.util.ProxyMessageUtils;
import org.niis.xroad.proxy.core.util.ProxyRequestContext;
import org.niis.xroad.proxy.core.util.RestRequestContext;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.ErrorCodes.X_SERVICE_FAILED_X;
import static ee.ria.xroad.common.util.HeaderValueUtils.getBoundary;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_AGREEMENT_TOKEN;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_MESSAGE_TYPE;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_ORIGINAL_CONTENT_TYPE;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_REQUEST_ID;
import static ee.ria.xroad.common.util.MimeUtils.VALUE_MESSAGE_TYPE_REST;
import static ee.ria.xroad.common.util.TimeUtils.getEpochMillisecond;
import static org.niis.xroad.common.core.exception.ErrorCode.ACCESS_DENIED;
import static org.niis.xroad.common.core.exception.ErrorCode.AGREEMENT_TOKEN_REJECTED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_DATA_PLANE_UNREACHABLE;
import static org.niis.xroad.common.core.exception.ErrorCode.INCONSISTENT_RESPONSE;
import static org.niis.xroad.common.core.exception.ErrorCode.IO_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.MISSING_REST;
import static org.niis.xroad.common.core.exception.ErrorCode.MISSING_SIGNATURE;
import static org.niis.xroad.common.core.exception.ErrorOrigin.DATASPACE;
import static org.niis.xroad.proxy.core.clientproxy.FastestConnectionSelectingSSLSocketFactory.ID_SELECTED_TARGET;
import static org.niis.xroad.proxy.core.clientproxy.FastestConnectionSelectingSSLSocketFactory.ID_TARGETS;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
@ArchUnitSuppressed("NoVanillaExceptions")
public class ClientRestMessageProcessor {

    private final MessageSigningService messageSigningService;
    private final HttpSenderProvider httpSenderProvider;
    private final ClientVerificationService clientVerificationService;
    private final OpMonitoringDataHelper opMonitoringDataHelper;
    private final GlobalConfProvider globalConfProvider;
    private final ProxyProperties proxyProperties;
    private final CommonProperties commonProperties;
    private final OcspVerifierFactory ocspVerifierFactory;
    private final ClientRequestPreparationService clientRequestPreparationService;
    private final DspRequestProcessor consumerSideDspProcessor;
    private final IdentifierValidationService identifierValidationService;
    private final LegacyCallRefusalPolicy legacyCallRefusalPolicy;
    private final DspSendFailureHandler dspSendFailureHandler;

    /**
     * Processes a REST message exchange described by the given request context.
     * @param ctx the per-request context carrying request, response, and monitoring data
     * @return {@code true} if the response was present and not an error response
     * @throws Exception if processing fails
     */
    @WithSpan
    public boolean process(RestRequestContext ctx) throws Exception {
        globalConfProvider.verifyValidity();

        var jRequest = ctx.request();
        var jResponse = ctx.response();
        var opMonitoringData = ctx.opMonitoringData();

        final String xRequestId = UUID.randomUUID().toString();
        if (opMonitoringData != null) {
            opMonitoringData.setXRequestId(xRequestId);
        }
        opMonitoringDataHelper.updateOpMonitoringClientSecurityServerAddress(opMonitoringData);

        ProxyMessage response = null;
        try {
            var restRequest = new RestRequest(
                    jRequest.getMethod(),
                    jRequest.getHttpURI().getPath(),
                    jRequest.getHttpURI().getQuery(),
                    headers(jRequest),
                    xRequestId
            );

            checkRequestIdentifiers(restRequest);

            var senderId = restRequest.getClientId();
            var requestServiceId = restRequest.getServiceId();

            clientVerificationService.verifyClientStatus(senderId);
            clientVerificationService.verifyClientAuthentication(senderId, jRequest);

            response = processRequest(jRequest, opMonitoringData, restRequest, senderId, requestServiceId, xRequestId, ctx);
            if (response != null) {
                sendResponse(response, jResponse);
            }
        } finally {
            if (response != null) {
                response.consume();
            }
        }
        return response != null
                && response.getRestResponse() != null
                && !response.getRestResponse().isErrorResponse();
    }

    boolean isManagementRequest(ServiceId serviceId) {
        return serviceId.getClientId().equals(globalConfProvider.getManagementRequestService());
    }

    private void checkRequestIdentifiers(RestRequest restRequest) {
        identifierValidationService.checkIdentifier(restRequest.getClientId());
        identifierValidationService.checkIdentifier(restRequest.getServiceId());
        identifierValidationService.checkIdentifier(restRequest.getTargetSecurityServer());
    }

    private ProxyMessage processRequest(RequestWrapper jRequest, OpMonitoringData opMonitoringData,
                                        RestRequest restRequest, ClientId senderId, ServiceId requestServiceId,
                                        String xRequestId, ProxyRequestContext ctx) throws Exception {
        if (restRequest.getQueryId() == null) {
            restRequest.setQueryId(globalConfProvider.getInstanceIdentifier() + "-" + UUID.randomUUID());
        }
        opMonitoringDataHelper.updateOpMonitoringDataByRestRequest(opMonitoringData, restRequest);
        clientRequestPreparationService.recordServiceSecurityServerAddress(
                requestServiceId, restRequest.getTargetSecurityServer(), ctx, opMonitoringData);

        ProxyMessage response;
        var sentRequest = send(jRequest, opMonitoringData, restRequest, senderId, requestServiceId, xRequestId, ctx);
        try (var httpSender = sentRequest.getHttpSender()) {
            var hashAlgoId = ProxyMessageUtils.getHashAlgoId(httpSender);
            response = parseResponse(httpSender, opMonitoringData, requestServiceId);
            checkConsistency(hashAlgoId, restRequest, sentRequest.getRestBodyDigest(), response);
        } catch (XrdRuntimeException e) {
            dropAssetAccessOnTokenRejection(e, restRequest, requestServiceId, sentRequest.isTokenPresented());
            throw e;
        }
        logResponseMessage(restRequest, response, xRequestId);
        return response;
    }

    void dropAssetAccessOnTokenRejection(XrdRuntimeException failure, RestRequest restRequest, ServiceId requestServiceId,
                                         boolean tokenPresented) {
        if (proxyProperties.dspEnabled() && (failure.isCausedBy(AGREEMENT_TOKEN_REJECTED)
                || tokenPresented && failure.isCausedBy(ACCESS_DENIED))) {
            consumerSideDspProcessor.invalidateAssetAccess(new DspRequest(requestServiceId, restRequest.getClientId(),
                    restRequest.getTargetSecurityServer(), isManagementRequest(requestServiceId)));
        }
    }

    /**
     * Sends the request and returns the sender of the successful attempt, left open so the response
     * can be parsed from it. Requests that go through DSP fail over to a sibling Security Server on a
     * transport fault; all other requests use the TLS handshake retry across the resolved addresses.
     */
    SentRequest send(RequestWrapper jRequest, OpMonitoringData opMonitoringData, RestRequest restRequest,
                     ClientId senderId, ServiceId requestServiceId, String xRequestId, ProxyRequestContext ctx)
            throws Exception {
        var dspRequest = new DspRequest(requestServiceId, restRequest.getClientId(),
                restRequest.getTargetSecurityServer(), isManagementRequest(requestServiceId));
        if (proxyProperties.dspEnabled() && !consumerSideDspProcessor.bypassesDsp(dspRequest)) {
            return sendWithDspFailover(dspRequest, jRequest, opMonitoringData, restRequest, senderId,
                    requestServiceId, xRequestId, ctx);
        }
        return sendWithHandshakeRetry(jRequest, opMonitoringData, restRequest, senderId, requestServiceId, xRequestId, ctx);
    }

    /**
     * Sends the request to the provider over the X-Road 7 path, transparently retrying towards another
     * security server when a TLS handshake failure surfaces mid-request (e.g. a TLS 1.3 server proxy
     * rejecting the client certificate after the handshake completed). The request entity is created
     * once and replayed byte-identically by subsequent attempts. Failed attempts' senders are closed here.
     */
    private SentRequest sendWithHandshakeRetry(RequestWrapper jRequest, OpMonitoringData opMonitoringData,
                                               RestRequest restRequest, ClientId senderId, ServiceId requestServiceId,
                                               String xRequestId, ProxyRequestContext ctx) throws Exception {
        final boolean retryEnabled = proxyProperties.clientProxy().enableRequestRetry();
        var entity = createRequestEntity(jRequest, opMonitoringData, restRequest, senderId, xRequestId, retryEnabled);
        try {
            for (int attempt = 1; ; attempt++) {
                var httpSender = httpSenderProvider.createClientHttpSender();
                try {
                    var addresses = clientRequestPreparationService.prepareRequest(httpSender, requestServiceId,
                            restRequest.getTargetSecurityServer(), ctx, opMonitoringData, null);
                    if (!proxyProperties.dspEnabled()) {
                        checkUnnegotiatedCallAllowed(requestServiceId);
                    }
                    addRequestHeaders(httpSender, xRequestId, null);
                    post(httpSender, addresses, entity, opMonitoringData);
                    return new SentRequest(httpSender, entity.getRestBodyDigest(), false);
                } catch (Exception e) {
                    var targets = httpSender.getAttribute(ID_TARGETS);
                    var failedAddress = httpSender.getAttribute(ID_SELECTED_TARGET);
                    httpSender.close();
                    if (!retryEnabled || !canRetry(e, entity, targets, attempt)) {
                        throw e;
                    }
                    log.warn("Sending the request to the server proxy failed with a TLS handshake error, retrying"
                                    + " (xRequestId: {}, attempt: {}, failed address: {})",
                            xRequestId, attempt, failedAddress, e);
                }
            }
        } finally {
            entity.consume();
        }
    }

    /**
     * Sends the request to the provider over DSP. After a transport fault on the data-plane send the
     * request is resent in full to the Security Server that a new acquisition selects, with at most as many
     * attempts as there are candidate Security Servers. Failures of the acquisition and of the request preparation
     * are thrown as they are.
     */
    private SentRequest sendWithDspFailover(DspRequest dspRequest, RequestWrapper jRequest,
                                            OpMonitoringData opMonitoringData, RestRequest restRequest,
                                            ClientId senderId, ServiceId requestServiceId, String xRequestId,
                                            ProxyRequestContext ctx) throws Exception {
        final boolean retryEnabled = proxyProperties.clientProxy().enableRequestRetry();
        var entity = createRequestEntity(jRequest, opMonitoringData, restRequest, senderId, xRequestId, retryEnabled);
        try {
            for (int attempt = 1; ; attempt++) {
                var acquisition = consumerSideDspProcessor.execute(dspRequest);
                var access = acquisition.access();
                var tokenPresented = StringUtils.isNotBlank(access.authorization());
                var httpSender = httpSenderProvider.createClientHttpSender();
                var addresses = prepareDspSender(httpSender, access, requestServiceId, opMonitoringData, xRequestId, ctx);
                try {
                    post(httpSender, addresses, entity, opMonitoringData);
                    return new SentRequest(httpSender, entity.getRestBodyDigest(), tokenPresented);
                } catch (Exception failure) {
                    httpSender.close();
                    dspSendFailureHandler.handle(failure, access);
                    if (!canFailOver(failure, attempt, acquisition.candidateCount(), retryEnabled, entity)) {
                        throw TransportFaultClassifier.isTransportFault(failure) ? dataPlaneUnreachable(failure) : failure;
                    }
                    log.warn("Sending the request over the data plane failed with a transport fault, acquiring access again"
                            + " (xRequestId: {}, attempt: {}, candidates: {})",
                            xRequestId, attempt, acquisition.candidateCount(), failure);
                }
            }
        } finally {
            entity.consume();
        }
    }

    /**
     * Tells whether a failed DSP send may be resent to another Security Server: the failure must be a transport
     * fault, fewer attempts than candidate Security Servers must have been made, request retry must be enabled
     * and the request entity must still be replayable.
     */
    static boolean canFailOver(Throwable failure, int attempt, int candidateCount, boolean retryEnabled,
                               SigningProxyMessageEntity entity) {
        return TransportFaultClassifier.isTransportFault(failure)
                && attempt < candidateCount
                && retryEnabled
                && entity.isReplayable();
    }

    private static XrdRuntimeException dataPlaneUnreachable(Throwable failure) {
        return ClientFacingErrorPolicy.sanitizeForConsumer(XrdRuntimeException.systemException(DSP_DATA_PLANE_UNREACHABLE)
                .origin(DATASPACE)
                .cause(failure)
                .details("No provider security server could be reached on the data plane")
                .build());
    }

    private SigningProxyMessageEntity createRequestEntity(RequestWrapper jRequest, OpMonitoringData opMonitoringData,
                                                          RestRequest restRequest, ClientId senderId,
                                                          String xRequestId, boolean retryEnabled) {
        final String contentType = MimeUtils.mpMixedContentType("xtop" + RandomStringUtils.secure().nextAlphabetic(30));
        if (opMonitoringData != null) {
            opMonitoringData.setRequestOutTs(getEpochMillisecond());
        }
        return new SigningProxyMessageEntity(contentType, messageSigningService, restRequest, senderId,
                jRequest, commonProperties.tempFilesPath(), opMonitoringData, xRequestId, retryEnabled);
    }

    /**
     * Tells whether a failed send attempt may be retried: the shared retry gate must pass
     * (TLS handshake failure, attempts capped at the resolved address count, a usable address
     * remaining) and the request entity must still be replayable.
     */
    private boolean canRetry(Exception exception, SigningProxyMessageEntity entity, Object targets, int attempt) {
        return clientRequestPreparationService.shouldRetry(exception, targets, attempt) && entity.isReplayable();
    }

    void checkUnnegotiatedCallAllowed(ServiceId requestServiceId) {
        legacyCallRefusalPolicy.checkConsumerCallAllowed(requestServiceId);
    }

    private URI[] prepareDspSender(HttpSender httpSender, AssetAccessResponse access, ServiceId requestServiceId,
                                   OpMonitoringData opMonitoringData, String xRequestId, ProxyRequestContext ctx) {
        try {
            var addresses = clientRequestPreparationService.prepareRequest(
                    httpSender, requestServiceId, URI.create(access.endpoint()), ctx, opMonitoringData, null);
            addRequestHeaders(httpSender, xRequestId, access.authorization());
            return addresses;
        } catch (Exception e) {
            httpSender.close();
            throw e;
        }
    }

    private static void addRequestHeaders(HttpSender httpSender, String xRequestId, @Nullable String agreementToken) {
        httpSender.addHeader(HEADER_MESSAGE_TYPE, VALUE_MESSAGE_TYPE_REST);
        httpSender.addHeader(HEADER_REQUEST_ID, xRequestId);
        if (StringUtils.isNotBlank(agreementToken)) {
            httpSender.addHeader(HEADER_AGREEMENT_TOKEN, agreementToken);
        }
    }

    private void post(HttpSender httpSender, URI[] addresses, SigningProxyMessageEntity entity,
                      OpMonitoringData opMonitoringData) throws Exception {
        try {
            httpSender.doPost(getServiceAddress(addresses), entity);
        } catch (Exception e) {
            clientRequestPreparationService.markAddressUnusableIfHandshakeFailure(httpSender, e);
            throw e;
        }
        if (opMonitoringData != null) {
            opMonitoringData.setResponseInTs(getEpochMillisecond());
        }
    }

    /**
     * Outcome of a successful send: the (still open) sender to parse the response from, the
     * digest of the streamed request body and whether an agreement token was sent with it.
     */
    @Getter
    @RequiredArgsConstructor
    static final class SentRequest {
        private final HttpSender httpSender;
        private final byte[] restBodyDigest;
        private final boolean tokenPresented;
    }

    private ProxyMessage parseResponse(HttpSender httpSender, OpMonitoringData opMonitoringData,
                                       ServiceId requestServiceId) throws IOException, MimeException {
        var response = new ProxyMessage(httpSender.getResponseHeaders().get(HEADER_ORIGINAL_CONTENT_TYPE),
                commonProperties.tempFilesPath());
        var decoder = new ProxyMessageDecoder(globalConfProvider,
                ocspVerifierFactory, response,
                httpSender.getResponseContentType(),
                ProxyMessageUtils.getHashAlgoId(httpSender));
        try {
            decoder.parse(httpSender.getResponseContent());
        } catch (XrdRuntimeException ex) {
            throw ex.withPrefix(X_SERVICE_FAILED_X);
        }
        updateOpMonitoringDataByResponse(opMonitoringData, decoder, response);
        checkResponse(response);
        if (opMonitoringData != null) {
            opMonitoringData.setRestResponseStatusCode(response.getRestResponse().getResponseCode());
        }
        decoder.verify(requestServiceId.getClientId(), response.getSignature());
        return response;
    }

    private static void updateOpMonitoringDataByResponse(OpMonitoringData opMonitoringData,
                                                         ProxyMessageDecoder decoder, ProxyMessage response) {
        if (opMonitoringData != null && response.getRestResponse() != null) {
            opMonitoringData.setResponseAttachmentCount(0);
            opMonitoringData.setResponseSize(response.getRestResponse().getMessageBytes().length
                    + decoder.getAttachmentsByteCount());
        }
    }

    private static void checkResponse(ProxyMessage response) {
        if (response.getFault() != null) {
            throw response.getFault().toXrdRuntimeException();
        }
        if (response.getRestResponse() == null) {
            throw XrdRuntimeException.systemException(MISSING_REST, "Response does not have REST message");
        }
        if (response.getSignature() == null) {
            throw XrdRuntimeException.systemException(MISSING_SIGNATURE, "Response does not have signature");
        }
    }

    private static void checkConsistency(DigestAlgorithm hashAlgoId, RestRequest restRequest,
                                         byte[] restBodyDigest, ProxyMessage response) throws IOException {
        if (!Objects.equals(restRequest.getClientId(), response.getRestResponse().getClientId())) {
            throw XrdRuntimeException.systemException(INCONSISTENT_RESPONSE, "Response client id does not match request message");
        }
        if (!Objects.equals(restRequest.getQueryId(), response.getRestResponse().getQueryId())) {
            throw XrdRuntimeException.systemException(INCONSISTENT_RESPONSE, "Response message id does not match request message");
        }
        if (!Objects.equals(restRequest.getServiceId(), response.getRestResponse().getServiceId())) {
            throw XrdRuntimeException.systemException(INCONSISTENT_RESPONSE, "Response service id does not match request message");
        }
        if (!Objects.equals(restRequest.getXRequestId(), response.getRestResponse().getXRequestId())) {
            throw XrdRuntimeException.systemException(INCONSISTENT_RESPONSE,
                    "Response message request id does not match request message");
        }

        byte[] requestDigest;
        if (restBodyDigest != null) {
            final DigestCalculator dc = Digests.createDigestCalculator(hashAlgoId);
            try (OutputStream out = dc.getOutputStream()) {
                out.write(restRequest.getHash());
                out.write(restBodyDigest);
            }
            requestDigest = dc.getDigest();
        } else {
            requestDigest = restRequest.getHash();
        }

        if (!Arrays.equals(requestDigest, response.getRestResponse().getRequestHash())) {
            throw XrdRuntimeException.systemException(INCONSISTENT_RESPONSE, "Response message hash does not match request message");
        }
    }

    private static void logResponseMessage(RestRequest restRequest, ProxyMessage response, String xRequestId) {
        MessageLog.log(restRequest,
                response.getRestResponse(),
                response.getSignature(),
                response.getRestBody(), true, xRequestId);
    }

    private static void sendResponse(ProxyMessage response, ResponseWrapper jResponse) throws IOException {
        final RestResponse rest = response.getRestResponse();
        jResponse.setStatus(rest.getResponseCode());

        for (Header h : rest.getHeaders()) {
            if ("Date".equalsIgnoreCase(h.getName())) {
                jResponse.putHeader(h.getName(), h.getValue());
            } else {
                jResponse.addHeader(h.getName(), h.getValue());
            }
        }
        if (response.hasRestBody()) {
            try (var out = jResponse.getOutputStream()) {
                IOUtils.copy(response.getRestBody(), out);
            }
        }
    }

    private URI getServiceAddress(URI[] addresses) {
        if (addresses.length == 1 || !proxyProperties.sslEnabled()) {
            return addresses[0];
        }
        return clientRequestPreparationService.getDummyServiceAddress();
    }

    private static List<Header> headers(RequestWrapper req) {
        return req.getHeaders().stream()
                .map(f -> new BasicHeader(f.getName(), f.getValue()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Named static entity that streams the signed REST request message to the server proxy.
     *
     * <p>The entity is repeatable: the first {@link #writeTo(OutputStream)} call materializes the
     * message — it streams the client request body while caching it, signs the message, and writes
     * exactly one message log record. Subsequent calls replay the byte-identical message from the
     * cached body, the captured OCSP responses, and the stored signature, without re-reading the
     * client stream, re-signing, or re-logging. When retry is enabled and the connection dies
     * mid-write, materialization still completes into the cache (the failed connection's writes are
     * discarded) so a later replay can resend the exact same message.
     *
     * <p>Note: {@link #isRepeatable()} returning {@code true} would also permit Apache HttpClient's
     * own retry handler to resend the entity. That handler is pinned to zero retries in
     * {@code ProxyClientConfig} and must stay disabled — resends are driven exclusively by the
     * processor-level retry loop.
     *
     * <p>The {@code restBodyDigest} field is populated during the first {@link #writeTo(OutputStream)}
     * and can be retrieved afterwards via {@link #getRestBodyDigest()}.
     */
    static class SigningProxyMessageEntity extends AbstractHttpEntity {

        private enum State { UNTOUCHED, MATERIALIZED, BROKEN }

        private final MessageSigningService messageSigningService;
        private final RestRequest restRequest;
        private final ClientId senderId;
        private final RequestWrapper jRequest;
        private final String tempFilesPath;
        private final OpMonitoringData opMonitoringData;
        private final String xRequestId;
        private final boolean retryEnabled;

        private State state = State.UNTOUCHED;
        private CachingStream bodyCache;
        private List<OCSPResp> ocspResponses;
        private SignatureData signature;

        @Getter
        private byte[] restBodyDigest;

        SigningProxyMessageEntity(String contentType, MessageSigningService messageSigningService,
                                  RestRequest restRequest, ClientId senderId, RequestWrapper jRequest,
                                  String tempFilesPath, OpMonitoringData opMonitoringData, String xRequestId,
                                  boolean retryEnabled) {
            super();
            setContentType(contentType);
            this.messageSigningService = messageSigningService;
            this.restRequest = restRequest;
            this.senderId = senderId;
            this.jRequest = jRequest;
            this.tempFilesPath = tempFilesPath;
            this.opMonitoringData = opMonitoringData;
            this.xRequestId = xRequestId;
            this.retryEnabled = retryEnabled;
        }

        @Override
        public boolean isRepeatable() {
            return true;
        }

        @Override
        public long getContentLength() {
            return -1;
        }

        @Override
        public InputStream getContent() throws UnsupportedOperationException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeTo(OutputStream outstream) {
            switch (state) {
                case UNTOUCHED -> materialize(outstream);
                case MATERIALIZED -> replay(outstream);
                default -> throw XrdRuntimeException.systemException(IO_ERROR,
                        "Request message is no longer replayable");
            }
        }

        /**
         * Encodes the message from the live client request stream, caching the body for message
         * logging and replays. With retry enabled, a connection failure mid-write does not abort
         * materialization: the remaining writes are discarded, the body finishes streaming into the
         * cache, the message is signed and logged, and the recorded failure is rethrown at the end.
         */
        private void materialize(OutputStream outstream) {
            state = State.BROKEN;
            final OutputStream out = retryEnabled ? new FailoverOutputStream(outstream) : outstream;
            try {
                final ProxyMessageEncoder enc = new ProxyMessageEncoder(out,
                        Digests.DEFAULT_DIGEST_ALGORITHM, getBoundary(contentType.getValue()));

                final CertChain chain = messageSigningService.getAuthKey().certChain();
                ocspResponses = messageSigningService.getAllOcspResponses(chain.getAllCertsWithoutTrustedRoot());
                ocspResponses.forEach(enc::ocspResponse);

                enc.restRequest(restRequest);
                encodeBodyAndLog(enc);

                signature = enc.getSignature();
                updateOpMonitoringData(enc);
                restBodyDigest = enc.getRestBodyDigest();
                enc.writeSignature();
                enc.close();
                state = State.MATERIALIZED;
            } catch (Exception e) {
                throw XrdRuntimeException.systemException(IO_ERROR, e);
            }
            if (out instanceof FailoverOutputStream failover) {
                failover.rethrowFailure();
            }
        }

        private void encodeBodyAndLog(ProxyMessageEncoder enc) throws IOException, CertificateEncodingException {
            try (InputStream in = jRequest.getInputStream()) {
                @SuppressWarnings("checkstyle:magicnumber")
                byte[] buf = new byte[4096];
                int count = in.read(buf);
                if (count >= 0) {
                    bodyCache = new CachingStream(tempFilesPath);
                    try (TeeInputStream tee = new TeeInputStream(in, bodyCache)) {
                        bodyCache.write(buf, 0, count);
                        enc.restBody(buf, count, tee);
                        enc.sign(messageSigningService.createSigningCtx(senderId));
                        MessageLog.log(restRequest, enc.getSignature(), bodyCache.getCachedContents(), true,
                                xRequestId);
                    }
                } else {
                    enc.sign(messageSigningService.createSigningCtx(senderId));
                    MessageLog.log(restRequest, enc.getSignature(), null, true, xRequestId);
                }
            }
        }

        /**
         * Re-encodes the byte-identical message from the cached body, the captured OCSP responses,
         * and the stored signature. Does not touch the client stream, the signer, or the message log.
         */
        private void replay(OutputStream outstream) {
            try {
                final ProxyMessageEncoder enc = new ProxyMessageEncoder(outstream,
                        Digests.DEFAULT_DIGEST_ALGORITHM, getBoundary(contentType.getValue()));
                ocspResponses.forEach(enc::ocspResponse);
                enc.restRequest(restRequest);
                if (bodyCache != null) {
                    enc.restBody(bodyCache.getCachedContents());
                }
                enc.signature(signature);
                enc.close();
            } catch (Exception e) {
                throw XrdRuntimeException.systemException(IO_ERROR, e);
            }
        }

        private void updateOpMonitoringData(ProxyMessageEncoder enc) {
            if (opMonitoringData != null) {
                opMonitoringData.setRequestAttachmentCount(0);
                opMonitoringData.setRequestSize(restRequest.getMessageBytes().length
                        + enc.getAttachmentsByteCount());
            }
        }

        /**
         * Tells whether {@link #writeTo(OutputStream)} can produce the complete message (again).
         */
        boolean isReplayable() {
            return state != State.BROKEN;
        }

        /**
         * Releases the cached request body. Idempotent; call once the entity is no longer needed.
         */
        void consume() {
            if (bodyCache != null) {
                bodyCache.consume();
            }
        }

        @Override
        public boolean isStreaming() {
            return true;
        }

    }

    /**
     * Output stream wrapper that records the first failure of the underlying stream and silently
     * discards all subsequent operations. Lets the message encoder finish materializing the message
     * (body caching, digest calculation, signing, message logging) when the connection dies
     * mid-write; the recorded failure is rethrown once materialization has completed.
     */
    static final class FailoverOutputStream extends OutputStream {

        private final OutputStream out;
        private IOException failure;

        FailoverOutputStream(OutputStream out) {
            this.out = out;
        }

        @Override
        public void write(int b) {
            if (failure == null) {
                try {
                    out.write(b);
                } catch (IOException e) {
                    failure = e;
                }
            }
        }

        @Override
        public void write(byte[] b, int off, int len) {
            if (failure == null) {
                try {
                    out.write(b, off, len);
                } catch (IOException e) {
                    failure = e;
                }
            }
        }

        @Override
        public void flush() {
            if (failure == null) {
                try {
                    out.flush();
                } catch (IOException e) {
                    failure = e;
                }
            }
        }

        @Override
        public void close() {
            try {
                out.close();
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                }
            }
        }

        /**
         * Rethrows the recorded stream failure, if any, wrapped the same way a direct write
         * failure would have been — keeping the original exception in the cause chain.
         */
        void rethrowFailure() {
            if (failure != null) {
                throw XrdRuntimeException.systemException(IO_ERROR, failure);
            }
        }
    }
}
