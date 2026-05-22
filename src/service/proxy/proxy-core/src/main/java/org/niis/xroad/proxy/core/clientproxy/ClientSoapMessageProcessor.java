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

import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.SoapMessageDecoder;
import ee.ria.xroad.common.message.SoapUtils;
import ee.ria.xroad.common.message.StaxEventSoapParserImpl;
import ee.ria.xroad.common.util.HttpSender;
import ee.ria.xroad.common.util.MimeUtils;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.Arrays;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.CommonProperties;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.dsp.DspRequest;
import org.niis.xroad.proxy.core.dsp.DspRequestProcessor;
import org.niis.xroad.proxy.core.messagelog.MessageLog;
import org.niis.xroad.proxy.core.protocol.ProxyMessage;
import org.niis.xroad.proxy.core.protocol.ProxyMessageDecoder;
import org.niis.xroad.proxy.core.service.ClientVerificationService;
import org.niis.xroad.proxy.core.service.HttpSenderProvider;
import org.niis.xroad.proxy.core.service.MessageSigningService;
import org.niis.xroad.proxy.core.util.ClientSoapRequestContext;
import org.niis.xroad.proxy.core.util.IdentifierValidationService;
import org.niis.xroad.proxy.core.util.OpMonitoringDataHelper;
import org.niis.xroad.proxy.core.util.ProxyMessageUtils;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static ee.ria.xroad.common.ErrorCodes.X_SERVICE_FAILED_X;
import static ee.ria.xroad.common.util.AbstractHttpSender.CHUNKED_LENGTH;
import static ee.ria.xroad.common.util.EncoderUtils.decodeBase64;
import static ee.ria.xroad.common.util.EncoderUtils.encodeBase64;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_ORIGINAL_CONTENT_TYPE;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_REQUEST_ID;
import static ee.ria.xroad.common.util.TimeUtils.getEpochMillisecond;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.niis.xroad.common.core.exception.ErrorCode.INCONSISTENT_RESPONSE;
import static org.niis.xroad.common.core.exception.ErrorCode.MISSING_SIGNATURE;
import static org.niis.xroad.common.core.exception.ErrorCode.MISSING_SOAP;

/**
 * Processes client-side SOAP messages as a CDI singleton.
 * All per-request state is held in method-local variables or on the per-request {@link SoapRequestDecoder}.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
@ArchUnitSuppressed("NoVanillaExceptions")
public class ClientSoapMessageProcessor {

    private static final int WAIT_FOR_SOAP_TIMEOUT = 30; // seconds

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

    private static final ExecutorService SOAP_HANDLER_EXECUTOR = createSoapHandlerExecutor();

    private static ExecutorService createSoapHandlerExecutor() {
        ThreadFactory factory = Thread.ofVirtual().name("soap-handler-executor", 0L).factory();

        var executor = Executors.newThreadPerTaskExecutor(factory);
        if (OpenTelemetry.noop().equals(GlobalOpenTelemetry.get())) {
            return executor;
        }

        log.info("OpenTelemetry is enabled, wrapping executor with OpenTelemetry context propagation");
        return Context.taskWrapping(executor);
    }

    /**
     * Processes a SOAP message exchange described by the given request context.
     *
     * @param ctx the per-request context carrying request, response, latches, piped streams and monitoring data
     * @return {@code true} if the response was present and had no fault
     * @throws Exception if processing fails
     */
    @WithSpan
    public boolean process(ClientSoapRequestContext ctx) throws Exception {
        log.trace("process()");

        globalConfProvider.verifyValidity();

        final String xRequestId = UUID.randomUUID().toString();
        var opMonitoringData = ctx.opMonitoringData();
        if (opMonitoringData != null) {
            opMonitoringData.setXRequestId(xRequestId);
        }
        opMonitoringDataHelper.updateOpMonitoringClientSecurityServerAddress(opMonitoringData);

        var decoder = new SoapRequestDecoder(ctx, messageSigningService, commonProperties.tempFilesPath(),
                xRequestId, proxyProperties, opMonitoringDataHelper);

        ProxyMessage response = null;
        Future<?> soapHandler = SOAP_HANDLER_EXECUTOR.submit(() -> handleSoap(ctx, decoder));

        try {
            // Wait for the request SOAP message to be parsed before we can start sending stuff.
            waitForSoapMessage(ctx);

            // If the handler thread excepted, do not continue.
            checkError(decoder);

            // Check that incoming identifiers do not contain illegal characters
            checkRequestIdentifiers(decoder);

            // Verify that the client is registered.
            ClientId client = decoder.getRequestSoap().getClient();
            clientVerificationService.verifyClientStatus(client);

            // Check client authentication mode.
            clientVerificationService.verifyClientAuthentication(client, ctx.request());

            response = processRequest(ctx, decoder, xRequestId, opMonitoringData);

            if (response != null) {
                sendResponse(response, ctx);
            }
        } catch (Exception e) {
            ctx.reqIns().close();

            // Let's interrupt the handler thread so that it won't block forever waiting for us to do something.
            soapHandler.cancel(true);

            throw e;
        } finally {
            if (response != null) {
                response.consume();
            }
        }
        return response != null && response.getFault() == null;
    }

    @WithSpan
    void handleSoap(ClientSoapRequestContext ctx, SoapRequestDecoder decoder) {
        try (decoder) {
            SoapMessageDecoder soapMessageDecoder = new SoapMessageDecoder(ctx.request().getContentType(), decoder,
                    new StaxEventSoapParserImpl());
            try {
                decoder.setOriginalSoapAction(SoapUtils.validateSoapActionHeader(ctx.request().getHeaders().get("SOAPAction")));
                soapMessageDecoder.parse(ctx.request().getInputStream());
            } catch (Exception ex) {
                throw XrdRuntimeException.systemException(ex).withPrefix(ErrorCodes.CLIENT_X);
            }
        } catch (Throwable ex) {
            decoder.setError(ex);
        } finally {
            decoder.continueProcessing();
            decoder.continueReadingResponse();
        }
    }

    boolean isManagementRequest(ServiceId serviceId) {
        return serviceId.getClientId().equals(globalConfProvider.getManagementRequestService());
    }

    private void checkRequestIdentifiers(SoapRequestDecoder decoder) {
        identifierValidationService.checkIdentifier(decoder.getRequestSoap().getClient());
        identifierValidationService.checkIdentifier(decoder.getRequestSoap().getService());
        identifierValidationService.checkIdentifier(decoder.getRequestSoap().getSecurityServer());
    }

    private ProxyMessage processRequest(ClientSoapRequestContext ctx, SoapRequestDecoder decoder,
                                        String xRequestId, OpMonitoringData opMonitoringData) throws Exception {
        log.trace("processRequest()");
        clientRequestPreparationService.recordServiceSecurityServerAddress(
                decoder.getServiceId(), decoder.getRequestSoap().getSecurityServer(), ctx, opMonitoringData);
        // MANAGEMENT requests target the mgmt participant context; all others use the host context.
        consumerSideDspProcessor.execute(new DspRequest(
                decoder.getServiceId(), decoder.getRequestSoap().getSecurityServer(),
                isManagementRequest(decoder.getServiceId())));
        ProxyMessage response;
        try (HttpSender httpSender = httpSenderProvider.createClientHttpSender()) {
            sendRequest(httpSender, ctx, decoder, xRequestId, opMonitoringData);

            // Check for any errors from the handler thread once more.
            waitForRequestSent(ctx);
            checkError(decoder);

            response = parseResponse(httpSender, decoder, opMonitoringData);
        }

        checkConsistency(decoder, response);

        logResponseMessage(response, xRequestId);

        return response;
    }

    private void sendRequest(HttpSender httpSender, ClientSoapRequestContext ctx, SoapRequestDecoder decoder,
                             String xRequestId, OpMonitoringData opMonitoringData) throws Exception {
        log.trace("sendRequest()");

        try {
            URI[] addresses = clientRequestPreparationService.prepareRequest(
                    httpSender, decoder.getServiceId(), decoder.getRequestSoap().getSecurityServer(),
                    ctx, opMonitoringData, decoder.getOriginalSoapAction());

            // Add unique id to distinguish request/response pairs
            httpSender.addHeader(HEADER_REQUEST_ID, xRequestId);

            if (opMonitoringData != null) {
                opMonitoringData.setRequestOutTs(getEpochMillisecond());
            }
            httpSender.doPost(getServiceAddress(addresses), ctx.reqIns(), CHUNKED_LENGTH, decoder.getOutputContentType());
            if (opMonitoringData != null) {
                opMonitoringData.setResponseInTs(getEpochMillisecond());
            }

        } finally {
            ctx.reqIns().close();
        }
    }

    private ProxyMessage parseResponse(HttpSender httpSender, SoapRequestDecoder decoder,
                                       OpMonitoringData opMonitoringData) throws Exception {
        log.trace("parseResponse()");

        ProxyMessage response = new ProxyMessage(httpSender.getResponseHeaders().get(HEADER_ORIGINAL_CONTENT_TYPE),
                commonProperties.tempFilesPath());

        ProxyMessageDecoder responseDecoder = new ProxyMessageDecoder(globalConfProvider,
                ocspVerifierFactory, response,
                httpSender.getResponseContentType(),
                ProxyMessageUtils.getHashAlgoId(httpSender));
        try {
            responseDecoder.parse(httpSender.getResponseContent());
        } catch (XrdRuntimeException ex) {
            throw ex.withPrefix(X_SERVICE_FAILED_X);
        }

        updateOpMonitoringDataByResponse(responseDecoder, response, opMonitoringData);

        // Ensure we have the required parts.
        checkResponse(response);

        responseDecoder.verify(decoder.getServiceId().getClientId(), response.getSignature());

        return response;
    }

    private static void updateOpMonitoringDataByResponse(ProxyMessageDecoder responseDecoder,
                                                         ProxyMessage response, OpMonitoringData opMonitoringData) {
        if (opMonitoringData != null && response.getSoap() != null) {
            long responseSize = response.getSoap().getBytes().length;

            opMonitoringData.setResponseSize(responseSize);
            opMonitoringData.setResponseAttachmentCount(responseDecoder.getAttachmentCount());

            if (responseDecoder.getAttachmentCount() > 0) {
                opMonitoringData.setResponseMimeSize(responseSize + responseDecoder.getAttachmentsByteCount());
            }
        }
    }

    private static void checkResponse(ProxyMessage response) {
        log.trace("checkResponse()");

        if (response.getFault() != null) {
            throw response.getFault().toXrdRuntimeException();
        }

        if (response.getSoap() == null) {
            throw XrdRuntimeException.systemException(MISSING_SOAP, "Response does not have SOAP message");
        }

        if (response.getSignature() == null) {
            throw XrdRuntimeException.systemException(MISSING_SIGNATURE, "Response does not have signature");
        }
    }

    private static void checkConsistency(SoapRequestDecoder decoder, ProxyMessage response) {
        log.trace("checkConsistency()");

        try {
            SoapUtils.checkConsistency(decoder.getRequestSoap(), response.getSoap());
        } catch (XrdRuntimeException e) {
            log.error("Inconsistent request-response", e);

            // The error code includes ServiceFailed because it indicates
            // faulty response from service (problem on the other side).
            throw XrdRuntimeException.systemException(INCONSISTENT_RESPONSE,
                    "Response from server proxy is not consistent with request").withPrefix(X_SERVICE_FAILED_X);
        }

        checkRequestHash(decoder, response);
    }

    private static void checkRequestHash(SoapRequestDecoder decoder, ProxyMessage response) {
        var requestHashFromResponse = response.getSoap().getHeader().getRequestHash();

        if (requestHashFromResponse != null) {
            byte[] requestHash = decoder.getRequestSoap().getHash();

            if (log.isTraceEnabled()) {
                log.trace("Calculated request message hash: {}\nRequest message (base64): {}",
                        encodeBase64(requestHash), encodeBase64(decoder.getRequestSoap().getBytes()));
            }

            if (!Arrays.areEqual(requestHash, decodeBase64(requestHashFromResponse.getHash()))) {
                throw XrdRuntimeException.systemException(INCONSISTENT_RESPONSE,
                        "Request message hash does not match request message");
            }
        } else {
            throw XrdRuntimeException.systemException(INCONSISTENT_RESPONSE,
                    "Response from server proxy is missing request message hash");
        }
    }

    private static void logResponseMessage(ProxyMessage response, String xRequestId) {
        log.trace("logResponseMessage()");

        MessageLog.log(response.getSoap(), response.getSignature(), response.getAttachments(), true, xRequestId);
    }

    private static void sendResponse(ProxyMessage response, ClientSoapRequestContext ctx) throws Exception {
        log.trace("sendResponse()");

        if (ctx.opMonitoringData() != null) {
            ctx.opMonitoringData().setResponseOutTs(getEpochMillisecond(), true);
        }

        ctx.response().setStatus(OK_200);
        ctx.response().setContentType(response.getSoapContentType(), MimeUtils.UTF8);

        try (var out = ctx.response().getOutputStream()) {
            response.writeSoapContent(out);
        }
    }

    private void waitForSoapMessage(ClientSoapRequestContext ctx) {
        log.trace("waitForSoapMessage()");

        try {
            if (!ctx.requestHandlerGate().await(WAIT_FOR_SOAP_TIMEOUT, TimeUnit.SECONDS)) {
                throw XrdRuntimeException.systemInternalError("Reading SOAP from request timed out");
            }
        } catch (InterruptedException e) {
            log.error("waitForSoapMessage interrupted", e);

            Thread.currentThread().interrupt();
        }
    }

    private static void waitForRequestSent(ClientSoapRequestContext ctx) {
        log.trace("waitForRequestSent()");

        try {
            ctx.httpSenderGate().await();
        } catch (InterruptedException e) {
            log.error("waitForRequestSent interrupted", e);

            Thread.currentThread().interrupt();
        }
    }

    private static void checkError(SoapRequestDecoder decoder) {
        if (decoder.getException() != null) {
            log.trace("checkError(): ", decoder.getException());

            throw decoder.getException();
        }
    }

    private URI getServiceAddress(URI[] addresses) {
        if (addresses.length == 1 || !proxyProperties.sslEnabled()) {
            return addresses[0];
        }
        // postpone actual name resolution to the fastest connection selector
        return clientRequestPreparationService.getDummyServiceAddress();
    }
}
