/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.proxy.clientproxy;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.cert.CertChain;
import ee.ria.xroad.common.conf.serverconf.IsAuthenticationData;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.RequestHash;
import ee.ria.xroad.common.message.SaxSoapParserImpl;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapMessage;
import ee.ria.xroad.common.message.SoapMessageDecoder;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapUtils;
import ee.ria.xroad.common.opmonitoring.OpMonitoringData;
import ee.ria.xroad.common.util.HttpSender;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.proxy.conf.KeyConf;
import ee.ria.xroad.proxy.messagelog.MessageLog;
import ee.ria.xroad.proxy.protocol.ProxyMessage;
import ee.ria.xroad.proxy.protocol.ProxyMessageDecoder;
import ee.ria.xroad.proxy.protocol.ProxyMessageEncoder;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.HttpClient;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.util.Arrays;

import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static ee.ria.xroad.common.ErrorCodes.X_INCONSISTENT_RESPONSE;
import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_MISSING_SIGNATURE;
import static ee.ria.xroad.common.ErrorCodes.X_MISSING_SOAP;
import static ee.ria.xroad.common.ErrorCodes.X_SERVICE_FAILED_X;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.SystemProperties.isSslEnabled;
import static ee.ria.xroad.common.util.AbstractHttpSender.CHUNKED_LENGTH;
import static ee.ria.xroad.common.util.CryptoUtils.decodeBase64;
import static ee.ria.xroad.common.util.CryptoUtils.encodeBase64;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_ORIGINAL_CONTENT_TYPE;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_ORIGINAL_SOAP_ACTION;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_REQUEST_ID;
import static ee.ria.xroad.common.util.TimeUtils.getEpochMillisecond;

@Slf4j
class ClientMessageProcessor extends AbstractClientMessageProcessor {

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

    /** Holds the incoming request SOAP message. */
    private volatile String originalSoapAction;
    private volatile SoapMessageImpl requestSoap;
    private volatile ServiceId requestServiceId;

    /** If the request failed, will contain SOAP fault. */
    private volatile CodedException executionException;

    /** Holds the proxy message output stream and associated info. */
    private PipedInputStream reqIns;
    private volatile PipedOutputStream reqOuts;
    private volatile String outputContentType;

    /** Holds the request to the server proxy. */
    private ProxyMessageEncoder request;
    private String xRequestId;

    /** Holds the response from server proxy. */
    private ProxyMessage response;

    private static final ExecutorService SOAP_HANDLER_EXECUTOR =
            createSoapHandlerExecutor();

    private static ExecutorService createSoapHandlerExecutor() {
        return Executors.newCachedThreadPool(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread handlerThread = new Thread(r);
                handlerThread.setName(Thread.currentThread().getName() + "-soap");

                return handlerThread;
            }
        });
    }

    ClientMessageProcessor(HttpServletRequest servletRequest, HttpServletResponse servletResponse,
            HttpClient httpClient, IsAuthenticationData clientCert, OpMonitoringData opMonitoringData)
            throws Exception {
        super(servletRequest, servletResponse, httpClient, clientCert, opMonitoringData);
        this.reqIns = new PipedInputStream();
        this.reqOuts = new PipedOutputStream(reqIns);
        this.xRequestId = UUID.randomUUID().toString();
    }

    @Override
    public void process() throws Exception {
        log.trace("process()");

        opMonitoringData.setXRequestId(xRequestId);
        updateOpMonitoringClientSecurityServerAddress();

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
        checkIdentifier(requestSoap.getClient());
        checkIdentifier(requestSoap.getService());
        checkIdentifier(requestSoap.getSecurityServer());
    }

    @Override
    public boolean verifyMessageExchangeSucceeded() {
        return response != null && response.getFault() == null;
    }

    private void updateOpMonitoringClientSecurityServerAddress() {
        try {
            opMonitoringData.setClientSecurityServerAddress(getSecurityServerAddress());
        } catch (Exception e) {
            log.error("Failed to assign operational monitoring data field {}",
                    OpMonitoringData.CLIENT_SECURITY_SERVER_ADDRESS, e);
        }
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

        response = new ProxyMessage(httpSender.getResponseHeaders().get(HEADER_ORIGINAL_CONTENT_TYPE));

        ProxyMessageDecoder decoder = new ProxyMessageDecoder(response, httpSender.getResponseContentType(),
                getHashAlgoId(httpSender));
        try {
            decoder.parse(httpSender.getResponseContent());
        } catch (CodedException ex) {
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

    private void checkResponse() throws Exception {
        log.trace("checkResponse()");

        if (response.getFault() != null) {
            throw response.getFault().toCodedException();
        }

        if (response.getSoap() == null) {
            throw new CodedException(X_MISSING_SOAP, "Response does not have SOAP message");
        }

        if (response.getSignature() == null) {
            throw new CodedException(X_MISSING_SIGNATURE, "Response does not have signature");
        }
    }

    private void checkConsistency() throws Exception {
        log.trace("checkConsistency()");

        try {
            SoapUtils.checkConsistency(requestSoap, response.getSoap());
        } catch (CodedException e) {
            log.error("Inconsistent request-response", e);

            // The error code includes ServiceFailed because it indicates
            // faulty response from service (problem on the other side).
            throw new CodedException(X_INCONSISTENT_RESPONSE,
                    "Response from server proxy is not consistent with request").withPrefix(X_SERVICE_FAILED_X);
        }

        checkRequestHash();
    }

    private void checkRequestHash() throws Exception {
        RequestHash requestHashFromResponse = response.getSoap().getHeader().getRequestHash();

        if (requestHashFromResponse != null) {
            byte[] requestHash = requestSoap.getHash();

            if (log.isTraceEnabled()) {
                log.trace("Calculated request message hash: {}\nRequest message (base64): {}",
                        encodeBase64(requestHash), encodeBase64(requestSoap.getBytes()));
            }

            if (!Arrays.areEqual(requestHash, decodeBase64(requestHashFromResponse.getHash()))) {
                throw new CodedException(X_INCONSISTENT_RESPONSE,
                        "Request message hash does not match request message");
            }
        } else {
            throw new CodedException(X_INCONSISTENT_RESPONSE,
                    "Response from server proxy is missing request message hash");
        }
    }

    private void logResponseMessage() throws Exception {
        log.trace("logResponseMessage()");

        MessageLog.log(response.getSoap(), response.getSignature(), true, xRequestId);
    }

    private void sendResponse() throws Exception {
        log.trace("sendResponse()");

        opMonitoringData.setResponseOutTs(getEpochMillisecond(), true);

        servletResponse.setStatus(HttpServletResponse.SC_OK);
        servletResponse.setCharacterEncoding(MimeUtils.UTF8);
        servletResponse.setContentType(response.getSoapContentType());

        try (InputStream is = response.getSoapContent()) {
            IOUtils.copy(is, servletResponse.getOutputStream());
        }
    }

    private void waitForSoapMessage() {
        log.trace("waitForSoapMessage()");

        try {
            if (!requestHandlerGate.await(WAIT_FOR_SOAP_TIMEOUT, TimeUnit.SECONDS)) {
                throw new CodedException(X_INTERNAL_ERROR, "Reading SOAP from request timed out");
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

    private void checkError() throws Exception {
        if (executionException != null) {
            log.trace("checkError(): ", executionException);

            throw executionException;
        }
    }

    private void setError(Throwable ex) {
        log.trace("setError()");

        if (executionException == null) {
            executionException = translateException(ex);
        }
    }

    public void handleSoap() {
        try (SoapMessageHandler handler = new SoapMessageHandler()) {
            SoapMessageDecoder soapMessageDecoder = new SoapMessageDecoder(servletRequest.getContentType(),
                    handler, new SaxSoapParserImpl());
            try {
                originalSoapAction = validateSoapActionHeader(servletRequest.getHeader("SOAPAction"));
                soapMessageDecoder.parse(servletRequest.getInputStream());
            } catch (Exception ex) {
                throw new ClientException(translateException(ex));
            }
        } catch (Throwable ex) {
            setError(ex);
        } finally {
            continueProcessing();
            continueReadingResponse();
        }
    }


    private class SoapMessageHandler implements SoapMessageDecoder.Callback {

        @Override
        public void soap(SoapMessage message, Map<String, String> headers) throws Exception {
            if (log.isTraceEnabled()) {
                log.trace("soap({})", message.getXml());
            }

            requestSoap = (SoapMessageImpl) message;
            requestServiceId = requestSoap.getService();

            updateOpMonitoringDataBySoapMessage(opMonitoringData, requestSoap);

            if (request == null) {
                request = new ProxyMessageEncoder(reqOuts, SoapUtils.getHashAlgoId());
                outputContentType = request.getContentType();
            }

            // We have the request SOAP message, we can start sending the
            // request to server proxy.
            continueProcessing();

            // In SSL mode, we need to send the OCSP response of our SSL cert.
            if (isSslEnabled()) {
                writeOcspResponses();
            }

            request.soap(requestSoap, headers);
        }

        @Override
        public void attachment(String contentType, InputStream content, Map<String, String> additionalHeaders)
                throws Exception {
            log.trace("attachment()");

            request.attachment(contentType, content, additionalHeaders);
        }

        @Override
        public void fault(SoapFault fault) throws Exception {
            onError(fault.toCodedException());
        }

        @Override
        public void onCompleted() {
            log.trace("onCompleted()");

            if (requestSoap == null) {
                setError(new ClientException(X_MISSING_SOAP, "Request does not contain SOAP message"));

                return;
            }

            updateOpMonitoringData();

            try {
                request.sign(KeyConf.getSigningCtx(requestSoap.getClient()));
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

        private void logRequestMessage() throws Exception {
            log.trace("logRequestMessage()");

            MessageLog.log(requestSoap, request.getSignature(), true, xRequestId);
        }

        @Override
        public void onError(Exception e) throws Exception {
            log.error("onError()", e);

            // Simply re-throw
            throw e;
        }

        private void writeOcspResponses() throws Exception {
            CertChain chain = KeyConf.getAuthKey().getCertChain();
            // exclude TopCA
            List<OCSPResp> ocspResponses = KeyConf.getAllOcspResponses(chain.getAllCertsWithoutTrustedRoot());

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
