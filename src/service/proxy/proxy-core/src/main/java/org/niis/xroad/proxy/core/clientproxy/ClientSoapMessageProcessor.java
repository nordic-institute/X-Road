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
import ee.ria.xroad.common.message.AttachmentStream;
import ee.ria.xroad.common.message.RequestHash;
import ee.ria.xroad.common.message.SaxSoapParserImpl;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapMessage;
import ee.ria.xroad.common.message.SoapMessageDecoder;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapUtils;
import ee.ria.xroad.common.util.CachingStream;
import ee.ria.xroad.common.util.HttpSender;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.TeeInputStream;
import org.apache.http.client.HttpClient;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.util.Arrays;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.ErrorOrigin;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.cert.CertChain;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory;
import org.niis.xroad.keyconf.KeyConfProvider;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.proxy.core.conf.SigningCtxProvider;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.messagelog.MessageLog;
import org.niis.xroad.proxy.core.protocol.Attachment;
import org.niis.xroad.proxy.core.protocol.ProxyMessage;
import org.niis.xroad.proxy.core.protocol.ProxyMessageDecoder;
import org.niis.xroad.proxy.core.protocol.ProxyMessageEncoder;
import org.niis.xroad.proxy.core.util.ClientAuthenticationService;
import org.niis.xroad.proxy.core.util.IdentifierValidator;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
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
import static ee.ria.xroad.common.util.MimeUtils.HEADER_ORIGINAL_SOAP_ACTION;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_REQUEST_ID;
import static ee.ria.xroad.common.util.TimeUtils.getEpochMillisecond;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.niis.xroad.common.core.exception.ErrorCode.INCONSISTENT_RESPONSE;
import static org.niis.xroad.common.core.exception.ErrorCode.MISSING_SIGNATURE;
import static org.niis.xroad.common.core.exception.ErrorCode.MISSING_SOAP;

@Slf4j
@ArchUnitSuppressed("NoVanillaExceptions")
public class ClientSoapMessageProcessor extends AbstractClientMessageProcessor {

    /**
     * Timeout for waiting for the SOAP message to be read from the request.
     */
    private static final int WAIT_FOR_SOAP_TIMEOUT = 30; // seconds

    /**
     * By using a count down latch we can make the main thread wait for the
     * request handler thread to read the SOAP request, since we cannot open
     * connection to server proxy before we haven't read the receiver name from
     * request SOAP.
     */
    private final CountDownLatch requestHandlerGate = new CountDownLatch(1);

    /**
     * By using a count down latch we can make the main thread wait for the
     * HTTP sender to finish sending the entire request to the piped output
     * stream, so we can check for errors in the handler thread before
     * receiving the response.
     */
    private final CountDownLatch httpSenderGate = new CountDownLatch(1);

    /**
     * Holds the incoming request SOAP message.
     */
    private volatile String originalSoapAction;
    private volatile SoapMessageImpl requestSoap;
    private volatile ServiceId requestServiceId;

    /**
     * If the request failed, will contain SOAP fault.
     */
    private volatile XrdRuntimeException executionException;

    /**
     * Holds the proxy message output stream and associated info.
     */
    private final PipedInputStream reqIns;
    private volatile PipedOutputStream reqOuts;
    private volatile String outputContentType;

    /**
     * Holds the request to the server proxy.
     */
    private ProxyMessageEncoder request;
    private final String xRequestId;

    /**
     * Holds the response from server proxy.
     */
    private ProxyMessage response;

    private final OcspVerifierFactory ocspVerifierFactory;
    private final KeyConfProvider keyConfProvider;
    private final SigningCtxProvider signingCtxProvider;
    private final String tempFilesPath;

    private final List<Attachment> attachmentCache = new ArrayList<>();

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

    public ClientSoapMessageProcessor(RequestWrapper request, ResponseWrapper response,
                                      ProxyProperties proxyProperties, GlobalConfProvider globalConfProvider,
                                      ServerConfProvider serverConfProvider, ClientAuthenticationService clientAuthenticationService,
                                      KeyConfProvider keyConfProvider, SigningCtxProvider signingCtxProvider,
                                      OcspVerifierFactory ocspVerifierFactory, String tempFilesPath,
                                      HttpClient httpClient, OpMonitoringData opMonitoringData)
            throws IOException {
        super(request, response, proxyProperties, globalConfProvider, serverConfProvider, clientAuthenticationService,
                httpClient, opMonitoringData);
        this.reqIns = new PipedInputStream();
        this.reqOuts = new PipedOutputStream(reqIns);
        this.xRequestId = UUID.randomUUID().toString();
        this.ocspVerifierFactory = ocspVerifierFactory;
        this.keyConfProvider = keyConfProvider;
        this.signingCtxProvider = signingCtxProvider;
        this.tempFilesPath = tempFilesPath;
    }

    @Override
    @WithSpan
    public void process() throws Exception {
        log.trace("process()");

        opMonitoringData.setXRequestId(xRequestId);
        opMonitoringDataHelper.updateOpMonitoringClientSecurityServerAddress(opMonitoringData);

        Future<?> soapHandler = SOAP_HANDLER_EXECUTOR.submit(this::handleSoap);

        try {
            // Wait for the request SOAP message to be parsed before we can start sending stuff.
            waitForSoapMessage();

            // If the handler thread excepted, do not continue.
            checkError();

            // Check that incoming identifiers do not contain illegal characters
            checkRequestIdentifiers();

            // Verify that the client is registered.
            ClientId client = requestSoap.getClient();
            verifyClientStatus(client);

            // Check client authentication mode.
            verifyClientAuthentication(client);

            processRequest();

            if (response != null) {
                sendResponse();
            }
        } catch (Exception e) {
            if (reqIns != null) {
                reqIns.close();
            }

            // Let's interrupt the handler thread so that it won't block forever waiting for us to do something.
            soapHandler.cancel(true);

            throw e;
        } finally {
            if (response != null) {
                response.consume();
            }
        }
    }

    private void checkRequestIdentifiers() {
        IdentifierValidator.checkIdentifier(requestSoap.getClient());
        IdentifierValidator.checkIdentifier(requestSoap.getService());
        IdentifierValidator.checkIdentifier(requestSoap.getSecurityServer());
    }

    @Override
    public boolean verifyMessageExchangeSucceeded() {
        return response != null && response.getFault() == null;
    }

    private void processRequest() throws Exception {
        log.trace("processRequest()");

        try (HttpSender httpSender = createHttpSender()) {
            sendRequest(httpSender);

            // Check for any errors from the handler thread once more.
            waitForRequestSent();
            checkError();

            parseResponse(httpSender);
        }

        checkConsistency();

        logResponseMessage();
    }

    private void sendRequest(HttpSender httpSender) throws Exception {
        log.trace("sendRequest()");

        try {
            URI[] addresses = prepareRequest(httpSender, requestServiceId, requestSoap.getSecurityServer());
            // Preserve the original SOAPAction header
            httpSender.addHeader(HEADER_ORIGINAL_SOAP_ACTION, originalSoapAction);

            // Add unique id to distinguish request/response pairs
            httpSender.addHeader(HEADER_REQUEST_ID, xRequestId);

            opMonitoringData.setRequestOutTs(getEpochMillisecond());
            httpSender.doPost(getServiceAddress(addresses), reqIns, CHUNKED_LENGTH, outputContentType);
            opMonitoringData.setResponseInTs(getEpochMillisecond());

        } finally {
            if (reqIns != null) {
                reqIns.close();
            }
        }
    }

    private void parseResponse(HttpSender httpSender) throws Exception {
        log.trace("parseResponse()");

        response = new ProxyMessage(httpSender.getResponseHeaders().get(HEADER_ORIGINAL_CONTENT_TYPE), tempFilesPath);

        ProxyMessageDecoder decoder = new ProxyMessageDecoder(globalConfProvider,
                ocspVerifierFactory, response,
                httpSender.getResponseContentType(),
                getHashAlgoId(httpSender));
        try {
            decoder.parse(httpSender.getResponseContent());
        } catch (XrdRuntimeException ex) {
            throw ex.withPrefix(X_SERVICE_FAILED_X);
        }

        updateOpMonitoringDataByResponse(decoder);

        // Ensure we have the required parts.
        checkResponse();

        decoder.verify(requestServiceId.getClientId(), response.getSignature());
    }

    private void updateOpMonitoringDataByResponse(ProxyMessageDecoder decoder) {
        if (response.getSoap() != null) {
            long responseSize = response.getSoap().getBytes().length;

            opMonitoringData.setResponseSize(responseSize);
            opMonitoringData.setResponseAttachmentCount(decoder.getAttachmentCount());

            if (decoder.getAttachmentCount() > 0) {
                opMonitoringData.setResponseMimeSize(responseSize + decoder.getAttachmentsByteCount());
            }
        }
    }

    private void checkResponse() {
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

    private void checkConsistency() {
        log.trace("checkConsistency()");

        try {
            SoapUtils.checkConsistency(requestSoap, response.getSoap());
        } catch (XrdRuntimeException e) {
            log.error("Inconsistent request-response", e);

            // The error code includes ServiceFailed because it indicates
            // faulty response from service (problem on the other side).
            throw XrdRuntimeException.systemException(INCONSISTENT_RESPONSE,
                    "Response from server proxy is not consistent with request").withPrefix(X_SERVICE_FAILED_X);
        }

        checkRequestHash();
    }

    private void checkRequestHash() {
        RequestHash requestHashFromResponse = response.getSoap().getHeader().getRequestHash();

        if (requestHashFromResponse != null) {
            byte[] requestHash = requestSoap.getHash();

            if (log.isTraceEnabled()) {
                log.trace("Calculated request message hash: {}\nRequest message (base64): {}",
                        encodeBase64(requestHash), encodeBase64(requestSoap.getBytes()));
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

    private void logResponseMessage() {
        log.trace("logResponseMessage()");

        MessageLog.log(response.getSoap(), response.getSignature(), response.getAttachments(), true, xRequestId);
    }

    private void sendResponse() throws Exception {
        log.trace("sendResponse()");

        opMonitoringData.setResponseOutTs(getEpochMillisecond(), true);

        jResponse.setStatus(OK_200);
        jResponse.setContentType(response.getSoapContentType(), MimeUtils.UTF8);

        try (var out = jResponse.getOutputStream()) {
            response.writeSoapContent(out);
        }
    }

    private void waitForSoapMessage() {
        log.trace("waitForSoapMessage()");

        try {
            if (!requestHandlerGate.await(WAIT_FOR_SOAP_TIMEOUT, TimeUnit.SECONDS)) {
                throw XrdRuntimeException.systemInternalError("Reading SOAP from request timed out");
            }
        } catch (InterruptedException e) {
            log.error("waitForSoapMessage interrupted", e);

            Thread.currentThread().interrupt();
        }
    }

    private void waitForRequestSent() {
        log.trace("waitForRequestSent()");

        try {
            httpSenderGate.await();
        } catch (InterruptedException e) {
            log.error("waitForRequestSent interrupted", e);

            Thread.currentThread().interrupt();
        }
    }

    private void continueProcessing() {
        log.trace("continueProcessing()");

        requestHandlerGate.countDown();
    }

    private void continueReadingResponse() {
        log.trace("continueReadingResponse()");

        httpSenderGate.countDown();
    }

    private void checkError() {
        if (executionException != null) {
            log.trace("checkError(): ", executionException);

            throw executionException;
        }
    }

    private void setError(Throwable ex) {
        log.trace("setError()");

        if (executionException == null) {
            executionException = XrdRuntimeException.systemException(ex);
        }
    }

    @WithSpan
    public void handleSoap() {
        try (SoapMessageHandler handler = new SoapMessageHandler()) {
            SoapMessageDecoder soapMessageDecoder = new SoapMessageDecoder(jRequest.getContentType(),
                    handler, new SaxSoapParserImpl());
            try {
                originalSoapAction = SoapUtils.validateSoapActionHeader(jRequest.getHeaders().get("SOAPAction"));
                soapMessageDecoder.parse(jRequest.getInputStream());
            } catch (Exception ex) {
                throw XrdRuntimeException.systemException(ex).withPrefix(ErrorCodes.CLIENT_X);
            }
        } catch (Throwable ex) {
            setError(ex);
        } finally {
            continueProcessing();
            continueReadingResponse();
        }
    }

    private final class SoapMessageHandler implements SoapMessageDecoder.Callback {

        @Override
        public void soap(SoapMessage message, Map<String, String> headers) throws IOException, CertificateEncodingException {
            if (log.isTraceEnabled()) {
                log.trace("soap({})", message.getXml());
            }

            requestSoap = (SoapMessageImpl) message;
            requestServiceId = requestSoap.getService();

            opMonitoringDataHelper.updateOpMonitoringDataBySoapMessage(opMonitoringData, requestSoap);

            if (request == null) {
                request = new ProxyMessageEncoder(reqOuts, SoapUtils.getHashAlgoId());
                outputContentType = request.getContentType();
            }

            // We have the request SOAP message, we can start sending the
            // request to server proxy.
            continueProcessing();

            // In SSL mode, we need to send the OCSP response of our SSL cert.
            if (proxyProperties.sslEnabled()) {
                writeOcspResponses();
            }

            request.soap(requestSoap, headers);
        }

        @Override
        public void attachment(String contentType, InputStream content, Map<String, String> additionalHeaders)
                throws IOException {
            log.trace("attachment()");

            CachingStream attachmentCacheStream = new CachingStream(tempFilesPath);
            try (TeeInputStream tis = new TeeInputStream(content, attachmentCacheStream)) {
                request.attachment(contentType, tis, additionalHeaders);
                attachmentCache.add(new Attachment(contentType, attachmentCacheStream, additionalHeaders));
            }
        }

        @Override
        @ArchUnitSuppressed("NoVanillaExceptions")
        public void fault(SoapFault fault) throws Exception {
            // client sent soap fault as request. not a valid case.
            // special handling to return fault fields from provided fault back to client with prefixed error code (backwards compatibility)
            log.info("SOAP fault message received from client as request. It is not valid.");
            var ex = XrdRuntimeException.systemException(ErrorCode.withCode(fault.getCode()))
                    .details(fault.getString())
                    .identifier(fault.getDetail())
                    .soapFaultInfo(ErrorOrigin.CLIENT.toPrefix() + fault.getCode(), fault.getString(),
                            fault.getActor(), fault.getDetail(), null)
                    .build();

            onError(ex);
        }

        @Override
        public void onCompleted() {
            log.trace("onCompleted()");

            if (requestSoap == null) {
                setError(XrdRuntimeException.systemException(MISSING_SOAP)
                        .details("Request does not contain SOAP message")
                        .origin(ErrorOrigin.CLIENT)
                        .build());

                return;
            }

            updateOpMonitoringData();

            try {
                request.sign(signingCtxProvider.createSigningCtx(requestSoap.getClient()));
                logRequestMessage();
                request.writeSignature();
            } catch (Exception ex) {
                setError(ex);
            }
        }

        private void updateOpMonitoringData() {
            opMonitoringData.setRequestAttachmentCount(request.getAttachmentCount());

            if (request.getAttachmentCount() > 0) {
                opMonitoringData.setRequestMimeSize(requestSoap.getBytes().length + request.getAttachmentsByteCount());
            }
        }

        private void logRequestMessage() {
            log.trace("logRequestMessage()");
            MessageLog.log(requestSoap, request.getSignature(), getAttachments(), true, xRequestId);
        }

        private List<AttachmentStream> getAttachments() {
            return attachmentCache.stream().map(Attachment::getAttachmentStream).toList();
        }

        @Override
        @ArchUnitSuppressed("NoVanillaExceptions")
        public void onError(Exception e) throws Exception {
            log.error("onError()", e);

            // Simply re-throw
            throw e;
        }

        private void writeOcspResponses() throws CertificateEncodingException, IOException {
            CertChain chain = keyConfProvider.getAuthKey().certChain();
            // exclude TopCA
            List<OCSPResp> ocspResponses = keyConfProvider.getAllOcspResponses(chain.getAllCertsWithoutTrustedRoot());

            for (OCSPResp ocsp : ocspResponses) {
                request.ocspResponse(ocsp);
            }
        }

        @Override
        public void close() {
            if (request != null) {
                try {
                    request.close();
                } catch (Exception e) {
                    setError(e);
                }
            }
        }
    }

}
