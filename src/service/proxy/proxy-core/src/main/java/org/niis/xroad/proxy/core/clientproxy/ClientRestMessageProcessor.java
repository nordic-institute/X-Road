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
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.Header;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.message.BasicHeader;
import org.apache.james.mime4j.MimeException;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.util.io.TeeInputStream;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.CommonProperties;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.cert.CertChain;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.dsp.DspRequest;
import org.niis.xroad.proxy.core.dsp.DspRequestProcessor;
import org.niis.xroad.proxy.core.messagelog.MessageLog;
import org.niis.xroad.proxy.core.protocol.ProxyMessage;
import org.niis.xroad.proxy.core.protocol.ProxyMessageDecoder;
import org.niis.xroad.proxy.core.protocol.ProxyMessageEncoder;
import org.niis.xroad.proxy.core.service.ClientVerificationService;
import org.niis.xroad.proxy.core.service.HttpSenderProvider;
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
import static ee.ria.xroad.common.util.MimeUtils.HEADER_MESSAGE_TYPE;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_ORIGINAL_CONTENT_TYPE;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_REQUEST_ID;
import static ee.ria.xroad.common.util.MimeUtils.VALUE_MESSAGE_TYPE_REST;
import static ee.ria.xroad.common.util.TimeUtils.getEpochMillisecond;
import static org.niis.xroad.common.core.exception.ErrorCode.INCONSISTENT_RESPONSE;
import static org.niis.xroad.common.core.exception.ErrorCode.IO_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.MISSING_REST;
import static org.niis.xroad.common.core.exception.ErrorCode.MISSING_SIGNATURE;
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
        var sentRequest = executeWithRetry(jRequest, opMonitoringData, restRequest, senderId,
                requestServiceId, xRequestId, ctx);
        try (var httpSender = sentRequest.httpSender()) {
            var hashAlgoId = ProxyMessageUtils.getHashAlgoId(httpSender);
            response = parseResponse(httpSender, opMonitoringData, requestServiceId);
            checkConsistency(hashAlgoId, restRequest, sentRequest.restBodyDigest(), response);
        }
        logResponseMessage(restRequest, response, xRequestId);
        return response;
    }

    /**
     * Sends the request, transparently retrying towards another security server when a TLS
     * handshake failure surfaces mid-request (e.g. a TLS 1.3 server proxy rejecting the client
     * certificate after the handshake completed). The request entity is created on the first
     * attempt and replayed byte-identically by subsequent attempts. Returns the sender of the
     * successful attempt, left open so the response can be parsed from it; failed attempts'
     * senders are closed here.
     */
    private SentRequest executeWithRetry(RequestWrapper jRequest, OpMonitoringData opMonitoringData,
                                         RestRequest restRequest, ClientId senderId, ServiceId requestServiceId,
                                         String xRequestId, ProxyRequestContext ctx) throws Exception {
        final boolean retryEnabled = proxyProperties.clientProxy().enableRequestRetry();
        SigningProxyMessageEntity entity = null;
        try {
            for (int attempt = 1; ; attempt++) {
                var httpSender = httpSenderProvider.createClientHttpSender();
                try {
                    if (entity == null) {
                        entity = createRequestEntity(jRequest, opMonitoringData, restRequest, senderId, xRequestId,
                                retryEnabled);
                    }
                    sendRequest(httpSender, entity, opMonitoringData, restRequest, requestServiceId, xRequestId, ctx);
                    return new SentRequest(httpSender, entity.getRestBodyDigest());
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
            if (entity != null) {
                entity.consume();
            }
        }
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
        return clientRequestPreparationService.shouldRetry(exception, targets, attempt)
                && entity != null && entity.isReplayable();
    }

    private void sendRequest(HttpSender httpSender, SigningProxyMessageEntity entity, OpMonitoringData opMonitoringData,
                             RestRequest restRequest, ServiceId requestServiceId, String xRequestId,
                             ProxyRequestContext ctx) throws Exception {
        log.trace("sendRequest()");

        // MANAGEMENT requests force the mgmt participant context; context selection otherwise happens per candidate in the processor.
        final URI[] addresses;
        if (proxyProperties.dspEnabled()) {
            var assetAccess = consumerSideDspProcessor.execute(new DspRequest(requestServiceId, restRequest.getClientId(),
                    restRequest.getTargetSecurityServer(), isManagementRequest(requestServiceId)));
            addresses = clientRequestPreparationService.prepareRequest(
                    httpSender, requestServiceId, URI.create(assetAccess.endpoint()), ctx, opMonitoringData, null);
        } else {
            addresses = clientRequestPreparationService.prepareRequest(
                    httpSender, requestServiceId, restRequest.getTargetSecurityServer(), ctx, opMonitoringData, null);
        }
        httpSender.addHeader(HEADER_MESSAGE_TYPE, VALUE_MESSAGE_TYPE_REST);
        httpSender.addHeader(HEADER_REQUEST_ID, xRequestId);

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
     * Outcome of a successful send: the (still open) sender to parse the response from and the
     * digest of the streamed request body.
     */
    private record SentRequest(HttpSender httpSender, byte[] restBodyDigest) {
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
