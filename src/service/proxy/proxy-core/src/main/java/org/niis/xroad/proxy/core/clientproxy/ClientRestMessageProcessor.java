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
    private final IdentifierValidationService identifierValidationService;

    /**
     * Processes a REST message exchange described by the given request context.
     *
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

        ProxyMessage response;
        try (var httpSender = httpSenderProvider.createClientHttpSender()) {
            var restBodyDigest = sendRequest(httpSender, jRequest, opMonitoringData, restRequest,
                    senderId, requestServiceId, xRequestId, ctx);
            var hashAlgoId = ProxyMessageUtils.getHashAlgoId(httpSender);
            response = parseResponse(httpSender, opMonitoringData, requestServiceId);
            checkConsistency(hashAlgoId, restRequest, restBodyDigest, response);
        }
        logResponseMessage(restRequest, response, xRequestId);
        return response;
    }

    private byte[] sendRequest(HttpSender httpSender, RequestWrapper jRequest, OpMonitoringData opMonitoringData,
                               RestRequest restRequest, ClientId senderId, ServiceId requestServiceId,
                               String xRequestId, ProxyRequestContext ctx) throws Exception {
        log.trace("sendRequest()");

        final URI[] addresses = clientRequestPreparationService.prepareRequest(
                httpSender, requestServiceId, restRequest.getTargetSecurityServer(), ctx, opMonitoringData, null);
        httpSender.addHeader(HEADER_MESSAGE_TYPE, VALUE_MESSAGE_TYPE_REST);
        httpSender.addHeader(HEADER_REQUEST_ID, xRequestId);

        final String contentType = MimeUtils.mpMixedContentType("xtop" + RandomStringUtils.secure().nextAlphabetic(30));
        if (opMonitoringData != null) {
            opMonitoringData.setRequestOutTs(getEpochMillisecond());
        }
        var entity = new SigningProxyMessageEntity(contentType, messageSigningService, restRequest, senderId,
                jRequest, commonProperties.tempFilesPath(), opMonitoringData, xRequestId);
        httpSender.doPost(getServiceAddress(addresses), entity);
        var restBodyDigest = entity.getRestBodyDigest();
        if (opMonitoringData != null) {
            opMonitoringData.setResponseInTs(getEpochMillisecond());
        }
        return restBodyDigest;
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
     * Named static entity that streams the signed REST request body to the server proxy.
     * The {@code restBodyDigest} field is populated during {@link #writeTo(OutputStream)} and
     * can be retrieved afterwards via {@link #getRestBodyDigest()}.
     */
    static class SigningProxyMessageEntity extends AbstractHttpEntity {

        private final MessageSigningService messageSigningService;
        private final RestRequest restRequest;
        private final ClientId senderId;
        private final RequestWrapper jRequest;
        private final String tempFilesPath;
        private final OpMonitoringData opMonitoringData;
        private final String xRequestId;

        @Getter
        private byte[] restBodyDigest;

        SigningProxyMessageEntity(String contentType, MessageSigningService messageSigningService,
                                  RestRequest restRequest, ClientId senderId, RequestWrapper jRequest,
                                  String tempFilesPath, OpMonitoringData opMonitoringData, String xRequestId) {
            super();
            setContentType(contentType);
            this.messageSigningService = messageSigningService;
            this.restRequest = restRequest;
            this.senderId = senderId;
            this.jRequest = jRequest;
            this.tempFilesPath = tempFilesPath;
            this.opMonitoringData = opMonitoringData;
            this.xRequestId = xRequestId;
        }

        @Override
        public boolean isRepeatable() {
            return false;
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
            try {
                final ProxyMessageEncoder enc = new ProxyMessageEncoder(outstream,
                        Digests.DEFAULT_DIGEST_ALGORITHM, getBoundary(contentType.getValue()));

                final CertChain chain = messageSigningService.getAuthKey().certChain();
                messageSigningService.getAllOcspResponses(chain.getAllCertsWithoutTrustedRoot())
                        .forEach(enc::ocspResponse);

                enc.restRequest(restRequest);

                try (InputStream in = jRequest.getInputStream()) {
                    @SuppressWarnings("checkstyle:magicnumber")
                    byte[] buf = new byte[4096];
                    int count = in.read(buf);
                    if (count >= 0) {
                        final CachingStream cache = new CachingStream(tempFilesPath);
                        try (TeeInputStream tee = new TeeInputStream(in, cache)) {
                            cache.write(buf, 0, count);
                            enc.restBody(buf, count, tee);
                            enc.sign(messageSigningService.createSigningCtx(senderId));
                            MessageLog.log(restRequest, enc.getSignature(), cache.getCachedContents(), true,
                                    xRequestId);
                        } finally {
                            cache.consume();
                        }
                    } else {
                        enc.sign(messageSigningService.createSigningCtx(senderId));
                        MessageLog.log(restRequest, enc.getSignature(), null, true, xRequestId);
                    }
                }

                if (opMonitoringData != null) {
                    opMonitoringData.setRequestAttachmentCount(0);
                    opMonitoringData.setRequestSize(restRequest.getMessageBytes().length
                            + enc.getAttachmentsByteCount());
                }

                restBodyDigest = enc.getRestBodyDigest();
                enc.writeSignature();
                enc.close();

            } catch (Exception e) {
                throw XrdRuntimeException.systemException(IO_ERROR, e);
            }
        }

        @Override
        public boolean isStreaming() {
            return true;
        }

    }
}
