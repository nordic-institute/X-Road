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

import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.AttachmentStream;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapMessage;
import ee.ria.xroad.common.message.SoapMessageDecoder;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapUtils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.TeeInputStream;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.ErrorOrigin;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.cert.CertChain;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.messagelog.MessageLog;
import org.niis.xroad.proxy.core.protocol.Attachment;
import org.niis.xroad.proxy.core.protocol.ProxyMessageEncoder;
import org.niis.xroad.proxy.core.service.MessageSigningService;
import org.niis.xroad.proxy.core.util.CachingStream;
import org.niis.xroad.proxy.core.util.OpMonitoringDataHelper;
import org.niis.xroad.proxy.core.util.ClientSoapRequestContext;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedOutputStream;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.niis.xroad.common.core.exception.ErrorCode.MISSING_SOAP;

/**
 * Per-request SOAP decoder that implements {@link SoapMessageDecoder.Callback}.
 * Created once per SOAP request and submitted to the SOAP_HANDLER_EXECUTOR.
 * The handler thread writes decoded SOAP data to instance fields, then signals the main thread via latches.
 * All fields are plain (non-volatile) because the CountDownLatch provides the happens-before guarantee.
 */
@Slf4j
public final class SoapRequestDecoder implements SoapMessageDecoder.Callback {

    private final ClientSoapRequestContext ctx;
    private final MessageSigningService messageSigningService;
    private final String tempFilesPath;
    private final String xRequestId;
    private final ProxyProperties proxyProperties;
    private final OpMonitoringDataHelper opMonitoringDataHelper;
    private final List<Attachment> attachmentCache = new ArrayList<>();

    private String originalSoapAction;
    private SoapMessageImpl requestSoap;
    private ServiceId requestServiceId;
    private XrdRuntimeException executionException;
    private PipedOutputStream reqOuts;
    private String outputContentType;
    private ProxyMessageEncoder encoder;

    /** Creates a new decoder for the given per-request SOAP context. */
    public SoapRequestDecoder(ClientSoapRequestContext ctx, MessageSigningService messageSigningService,
                              String tempFilesPath, String xRequestId,
                              ProxyProperties proxyProperties, OpMonitoringDataHelper opMonitoringDataHelper) {
        this.ctx = ctx;
        this.messageSigningService = messageSigningService;
        this.tempFilesPath = tempFilesPath;
        this.xRequestId = xRequestId;
        this.proxyProperties = proxyProperties;
        this.opMonitoringDataHelper = opMonitoringDataHelper;
        this.reqOuts = ctx.reqOuts();
    }

    @Override
    public void soap(SoapMessage message, Map<String, String> headers) throws IOException, CertificateEncodingException {
        if (log.isTraceEnabled()) {
            log.trace("soap({})", message.getXml());
        }

        requestSoap = (SoapMessageImpl) message;
        requestServiceId = requestSoap.getService();

        opMonitoringDataHelper.updateOpMonitoringDataBySoapMessage(ctx.opMonitoringData(), requestSoap);

        if (encoder == null) {
            encoder = new ProxyMessageEncoder(reqOuts, SoapUtils.getHashAlgoId());
            outputContentType = encoder.getContentType();
        }

        // We have the request SOAP message, we can start sending the request to server proxy.
        continueProcessing();

        // In SSL mode, we need to send the OCSP response of our SSL cert.
        if (proxyProperties.sslEnabled()) {
            writeOcspResponses();
        }

        encoder.soap(requestSoap, headers);
    }

    private void writeOcspResponses() throws CertificateEncodingException, IOException {
        CertChain chain = messageSigningService.getAuthKey().certChain();
        List<OCSPResp> ocspResponses = messageSigningService.getAllOcspResponses(chain.getAllCertsWithoutTrustedRoot());

        for (OCSPResp ocsp : ocspResponses) {
            encoder.ocspResponse(ocsp);
        }
    }

    @Override
    public void attachment(String contentType, InputStream content, Map<String, String> additionalHeaders)
            throws IOException {
        log.trace("attachment()");

        CachingStream attachmentCacheStream = new CachingStream(tempFilesPath);
        try (TeeInputStream tis = new TeeInputStream(content, attachmentCacheStream)) {
            encoder.attachment(contentType, tis, additionalHeaders);
            attachmentCache.add(new Attachment(contentType, attachmentCacheStream, additionalHeaders));
        }
    }

    @Override
    @ArchUnitSuppressed("NoVanillaExceptions")
    public void fault(SoapFault fault) throws Exception {
        // client sent soap fault as request — not a valid case.
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
            encoder.sign(messageSigningService.createSigningCtx(requestSoap.getClient()));
            logRequestMessage();
            encoder.writeSignature();
        } catch (Exception ex) {
            setError(ex);
        }
    }

    private void updateOpMonitoringData() {
        if (ctx.opMonitoringData() != null) {
            ctx.opMonitoringData().setRequestAttachmentCount(encoder.getAttachmentCount());

            if (encoder.getAttachmentCount() > 0) {
                ctx.opMonitoringData().setRequestMimeSize(requestSoap.getBytes().length + encoder.getAttachmentsByteCount());
            }
        }
    }

    private void logRequestMessage() {
        log.trace("logRequestMessage()");
        MessageLog.log(requestSoap, encoder.getSignature(), getAttachmentStreams(), true, xRequestId);
    }

    private List<AttachmentStream> getAttachmentStreams() {
        return attachmentCache.stream().map(Attachment::getAttachmentStream).toList();
    }

    @Override
    @ArchUnitSuppressed("NoVanillaExceptions")
    public void onError(Exception e) throws Exception {
        log.error("onError()", e);

        // Simply re-throw
        throw e;
    }

    @Override
    public void close() {
        if (encoder != null) {
            try {
                encoder.close();
            } catch (Exception e) {
                setError(e);
            }
        }
    }

    // --- Latch coordination ---

    /**
     * Signals the main thread that the SOAP request has been parsed (or an error occurred).
     */
    public void continueProcessing() {
        log.trace("continueProcessing()");
        ctx.requestHandlerGate().countDown();
    }

    /**
     * Signals the main thread that writing to the piped output stream is complete.
     */
    public void continueReadingResponse() {
        log.trace("continueReadingResponse()");
        ctx.httpSenderGate().countDown();
    }

    /**
     * Records an execution error. Only the first error is retained.
     *
     * @param ex the exception to record
     */
    public void setError(Throwable ex) {
        log.trace("setError()");
        if (executionException == null) {
            executionException = XrdRuntimeException.systemException(ex);
        }
    }

    // --- Public getters for main thread to read after latch await ---

    /** Returns the parsed SOAP request message, or {@code null} if not yet parsed. */
    public SoapMessageImpl getRequestSoap() {
        return requestSoap;
    }

    /** Returns the service identifier extracted from the SOAP request, or {@code null} if not yet parsed. */
    public ServiceId getServiceId() {
        return requestServiceId;
    }

    /**
     * Returns the execution exception if the handler thread encountered an error.
     *
     * @return the exception, or {@code null} if no error occurred
     */
    public XrdRuntimeException getException() {
        return executionException;
    }

    /** Returns the content type of the encoded proxy message output, or {@code null} if encoding has not started. */
    public String getOutputContentType() {
        return outputContentType;
    }

    /** Returns the original SOAPAction header value, or {@code null} if not yet set. */
    public String getOriginalSoapAction() {
        return originalSoapAction;
    }

    /** Sets the original SOAPAction header value. */
    public void setOriginalSoapAction(String originalSoapAction) {
        this.originalSoapAction = originalSoapAction;
    }

    /** Returns the proxy message encoder, or {@code null} if encoding has not started. */
    public ProxyMessageEncoder getEncoder() {
        return encoder;
    }

    /** Returns the cached list of attachments from the SOAP request. */
    public List<Attachment> getAttachments() {
        return attachmentCache;
    }
}
