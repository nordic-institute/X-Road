/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.proxy.clientproxy;

import ee.ria.xroad.asyncdb.AsyncDB;
import ee.ria.xroad.asyncdb.WritingCtx;
import ee.ria.xroad.asyncdb.messagequeue.MessageQueue;
import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.cert.CertChain;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.IsAuthentication;
import ee.ria.xroad.common.conf.serverconf.IsAuthenticationData;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.*;
import ee.ria.xroad.common.monitoring.MessageInfo;
import ee.ria.xroad.common.monitoring.MessageInfo.Origin;
import ee.ria.xroad.common.monitoring.MonitorAgent;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.HttpSender;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.proxy.ProxyMain;
import ee.ria.xroad.proxy.conf.KeyConf;
import ee.ria.xroad.proxy.messagelog.MessageLog;
import ee.ria.xroad.proxy.protocol.ProxyMessage;
import ee.ria.xroad.proxy.protocol.ProxyMessageDecoder;
import ee.ria.xroad.proxy.protocol.ProxyMessageEncoder;
import ee.ria.xroad.proxy.util.MessageProcessorBase;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.HttpClient;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.util.Arrays;
import org.eclipse.jetty.http.MimeTypes;
import org.w3c.dom.Node;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.Marshaller;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPMessage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static ee.ria.xroad.common.ErrorCodes.*;
import static ee.ria.xroad.common.util.AbstractHttpSender.CHUNKED_LENGTH;
import static ee.ria.xroad.common.util.CryptoUtils.*;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_HASH_ALGO_ID;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_PROXY_VERSION;
import static ee.ria.xroad.proxy.clientproxy.FastestConnectionSelectingSSLSocketFactory.ID_TARGETS;

@Slf4j
class ClientMessageProcessor extends MessageProcessorBase {

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
     * Holds the client side SSL certificate.
     */
    private final IsAuthenticationData clientCert;

    /** Holds the incoming request SOAP message. */
    private volatile SoapMessageImpl requestSoap;
    private volatile ServiceId requestServiceId;

    /** Holds true, if the incoming message should be treated as asynchronous.
     * An incoming message should be treated as asynchronous (sent to async-db)
     * if the message header contains the async field and HTTP request headers
     * do not contain X-Ignore-Async field. */
    private volatile boolean isAsync;

    /** If the request failed, will contain SOAP fault. */
    private volatile CodedException executionException;

    /** Holds the proxy message output stream and associated info. */
    private PipedInputStream reqIns;
    private volatile PipedOutputStream reqOuts;
    private volatile String outputContentType;

    /** Holds the request to the server proxy. */
    private ProxyMessageEncoder request;

    /** Holds the response from server proxy. */
    private ProxyMessage response;

    ClientMessageProcessor(HttpServletRequest servletRequest,
            HttpServletResponse servletResponse, HttpClient httpClient,
            IsAuthenticationData clientCert) throws Exception {
        super(servletRequest, servletResponse, httpClient);
        this.clientCert = clientCert;
        this.reqIns = new PipedInputStream();
        this.reqOuts = new PipedOutputStream(reqIns);
    }

    SoapMessageImpl getRequestSoap() {
        return requestSoap;
    }

    @Override
    public void process() throws Exception {
        log.trace("process()");

        HandlerThread handlerThread = new HandlerThread();
        handlerThread.setName(Thread.currentThread().getName() + "-soap");

        handlerThread.start();
        try {
            // Wait for the request SOAP message to be parsed before we can
            // start sending stuff.
            waitForSoapMessage();

            // If the handler thread excepted, do not continue.
            checkError();

            // Verify that the client is registered
            verifyClientStatus();

            // Check client authentication mode
            verifyClientAuthentication();

            // If the message is synchronous, start sending proxy message
            if (!isAsync) {
                processRequest();
            }

            if (response != null) {
                sendResponse();
            }
        } catch (Exception e) {
            if (reqIns != null) {
                reqIns.close();
            }

            // Let's interrupt the handler thread so that it won't
            // block forever waiting for us to do something.
            handlerThread.interrupt();
            throw e;
        } finally {
            handlerThread.join();

            if (response != null) {
                response.consume();
            }
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
            // If we're using SSL, we need to include the provider name in
            // the HTTP request so that server proxy could verify the SSL
            // certificate properly.
            if (SystemProperties.isSslEnabled()) {
                httpSender.setAttribute(AuthTrustVerifier.ID_PROVIDERNAME,
                        requestServiceId);
            }

            // Start sending the request to server proxies. The underlying
            // SSLConnectionSocketFactory will select the fastest address
            // (socket that connects first) from the provided addresses.
            // Dummy service address is only needed so that host name resolving
            // could do its thing and start the ssl connection.
            URI[] addresses = getServiceAddresses(requestServiceId, requestSoap.getSecurityServer());
            httpSender.setAttribute(ID_TARGETS, addresses);
            httpSender.setTimeout(SystemProperties.getClientProxyTimeout());

            httpSender.addHeader(HEADER_HASH_ALGO_ID, getHashAlgoId());
            httpSender.addHeader(HEADER_PROXY_VERSION, ProxyMain.getVersion());

            try {
                httpSender.doPost(getDummyServiceAddress(addresses), reqIns,
                        CHUNKED_LENGTH, outputContentType);
            } catch (Exception e) {
                // Failed to connect to server proxy
                MonitorAgent.serverProxyFailed(createRequestMessageInfo());
                // Rethrow
                throw e;
            }
        } finally {
            if (reqIns != null) {
                reqIns.close();
            }
        }
    }

    private void parseResponse(HttpSender httpSender) throws Exception {
        log.trace("parseResponse()");

        response = new ProxyMessage();

        ProxyMessageDecoder decoder = new ProxyMessageDecoder(response,
                httpSender.getResponseContentType(),
                getHashAlgoId(httpSender));
        try {
            decoder.parse(httpSender.getResponseContent());
        } catch (CodedException ex) {
            throw ex.withPrefix(X_SERVICE_FAILED_X);
        }

        // Ensure we have the required parts.
        checkResponse();

        decoder.verify(requestServiceId.getClientId(), response.getSignature());
    }

    private void checkResponse() throws Exception {
        log.trace("checkResponse()");

        if (response.getFault() != null) {
            throw response.getFault().toCodedException();
        }

        if (response.getSoap() == null) {
            throw new CodedException(X_MISSING_SOAP,
                    "Response does not have SOAP message");
        }

        if (response.getSignature() == null) {
            throw new CodedException(X_MISSING_SIGNATURE,
                    "Response does not have signature");
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
                    "Response from server proxy is not consistent with request")
                    .withPrefix(X_SERVICE_FAILED_X);
        }

        checkRequestHash();
    }

    private void checkRequestHash() throws Exception {
        RequestHash requestHashFromResponse =
                response.getSoap().getHeader().getRequestHash();
        if (requestHashFromResponse != null) {
            byte[] requestHash = calculateDigest(
                    getAlgorithmId(
                            requestHashFromResponse.getAlgorithmId()),
                    requestSoap.getBytes());

            if (log.isTraceEnabled()) {
                log.trace("Calculated request message hash: {}\n"
                        + "Request message (base64): {}",
                        encodeBase64(requestHash),
                        encodeBase64(requestSoap.getBytes()));
            }

            if (!Arrays.areEqual(requestHash, decodeBase64(
                    requestHashFromResponse.getHash()))) {
                throw new CodedException(X_INCONSISTENT_RESPONSE,
                        "Request message hash does not match request message");
            }
        } else {
            throw new CodedException(X_INCONSISTENT_RESPONSE,
                    "Response from server proxy is missing request message "
                            + "hash");
        }
    }

    private void logRequestMessage() throws Exception {
        if (request != null) {
            log.trace("logRequestMessage()");

            MessageLog.log(requestSoap, request.getSignature(), true);
        }
    }

    private void logResponseMessage() throws Exception {
        log.trace("logResponseMessage()");

        MessageLog.log(response.getSoap(), response.getSignature(), true);
    }

    private void sendResponse() throws Exception {
        log.trace("sendResponse()");

        servletResponse.setStatus(HttpServletResponse.SC_OK);
        servletResponse.setHeader("SOAPAction", "");
        servletResponse.setCharacterEncoding(MimeUtils.UTF8);

        servletResponse.setContentType(response.getSoapContentType());
        try (InputStream is = response.getSoapContent()) {
            IOUtils.copy(is, servletResponse.getOutputStream());
        }
    }

    private void waitForSoapMessage() {
        log.trace("waitForSoapMessage()");
        try {
            if (!requestHandlerGate.await(WAIT_FOR_SOAP_TIMEOUT,
                    TimeUnit.SECONDS)) {
                throw new CodedException(X_INTERNAL_ERROR,
                        "Reading SOAP from request timed out");
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

    @Override
    public MessageInfo createRequestMessageInfo() {
        if (requestSoap == null) {
            return null;
        }

        return new MessageInfo(Origin.CLIENT_PROXY, requestSoap.getClient(),
                requestServiceId, requestSoap.getUserId(),
                requestSoap.getQueryId());
    }

    protected void verifyClientStatus() throws Exception {
        ClientId client = requestSoap.getClient();

        String status = ServerConf.getMemberStatus(client);
        if (!ClientType.STATUS_REGISTERED.equals(status)) {
            throw new CodedException(X_UNKNOWN_MEMBER, "Client '%s' not found",
                    client);
        }
    }

    protected void verifyClientAuthentication() throws Exception {
        if (!SystemProperties.shouldVerifyClientCert()) {
            return;
        }

        log.trace("verifyClientAuthentication()");

        ClientId sender = requestSoap.getClient();
        IsAuthentication.verifyClientAuthentication(sender, clientCert);
    }

    private static URI getDummyServiceAddress(URI[] addresses)
            throws Exception {
        if (!SystemProperties.isSslEnabled()) {
            // In non-ssl mode we just connect to the first address
            return addresses[0];
        }
        final int port = SystemProperties.getServerProxyPort();
        return new URI("https", null, "localhost", port, "/", null, null);
    }

    private static URI[] getServiceAddresses(ServiceId serviceProvider, SecurityServerId serverId)
            throws Exception {
        log.trace("getServiceAddresses({})", serviceProvider);

        Collection<String> hostNames =
                GlobalConf.getProviderAddress(serviceProvider.getClientId());
        if (hostNames == null || hostNames.isEmpty()) {
            throw new CodedException(X_UNKNOWN_MEMBER,
                    "Could not find addresses for service provider \"%s\"",
                    serviceProvider);
        }

        if (serverId != null) {
            final String securityServerAddress = GlobalConf.getSecurityServerAddress(serverId);
            if (securityServerAddress == null) {
                throw new CodedException(X_INVALID_SECURITY_SERVER,
                        "Could not find security server \"%s\"",
                        serverId);
            }

            if (!hostNames.contains(securityServerAddress)) {
                throw new CodedException(X_INVALID_SECURITY_SERVER,
                        "Invalid security server \"%s\"",
                        serviceProvider);
            }

            hostNames = Collections.singleton(securityServerAddress);
        }

        String protocol = SystemProperties.isSslEnabled() ? "https" : "http";
        int port = SystemProperties.getServerProxyPort();

        List<URI> addresses = new ArrayList<>(hostNames.size());
        for (String host : hostNames) {
            addresses.add(new URI(protocol, null, host, port, "/", null, null));
        }

        return addresses.toArray(new URI[] {});
    }

    private static String getHashAlgoId() {
        // TODO #2578 make hash function configurable
        return CryptoUtils.DEFAULT_DIGEST_ALGORITHM_ID;
    }

    private static String getHashAlgoId(HttpSender httpSender) {
        return httpSender.getResponseHeaders().get(HEADER_HASH_ALGO_ID);
    }

    private class HandlerThread extends Thread {
        @Override
        public void run() {
            try (SoapMessageHandler handler = new SoapMessageHandler()) {
                SoapMessageDecoder soapMessageDecoder =
                        new SoapMessageDecoder(servletRequest.getContentType(),
                                handler, new RequestSoapParserImpl());
                try {
                    soapMessageDecoder.parse(servletRequest.getInputStream());
                } catch (Exception ex) {
                    throw new ClientException(translateException(ex));
                }
            } catch (Exception ex) {
                setError(ex);
            } finally {
                continueProcessing();
                continueReadingResponse();
            }
        }
    }

    /** This is wrapper class that internally selects the proper handler
     * based on the SOAP message. If the SOAP message is asynchronous, then
     * AsyncSoapMessageHandler is used, otherwise DefaultSoapMessageHandler
     * is used. */
    private class SoapMessageHandler implements SoapMessageDecoder.Callback {

        private SoapMessageDecoder.Callback handler;

        @Override
        public void soap(SoapMessage message) throws Exception {
            log.trace("soap({})", message.getXml());

            requestSoap = (SoapMessageImpl) message;
            requestServiceId = requestSoap.getService();

            if (handler == null) {
                chooseHandler();
            }

            handler.soap(message);
        }

        @Override
        public void attachment(String contentType, InputStream content,
                Map<String, String> additionalHeaders) throws Exception {
            log.trace("attachment({})", contentType);

            if (handler != null) {
                handler.attachment(contentType, content, additionalHeaders);
            } else {
                throw new CodedException(X_INTERNAL_ERROR,
                        "No soap message handler present");
            }
        }

        @Override
        public void fault(SoapFault fault) throws Exception {
            onError(fault.toCodedException());
        }

        private void chooseHandler() {
            isAsync = requestSoap.isAsync() && (servletRequest.getHeader(
                    SoapUtils.X_IGNORE_ASYNC) == null);
            if (isAsync) {
                log.trace("Creating handler for asynchronous messages");
                handler = new AsyncSoapMessageHandler();
            } else {
                log.trace("Creating handler for normal messages");
                handler = new DefaultSoapMessageHandler();
            }
        }

        @Override
        public void onCompleted() {
            log.trace("onCompleted()");

            if (requestSoap == null) {
                setError(new ClientException(X_MISSING_SOAP,
                        "Request does not contain SOAP message"));
                return;
            }

            if (handler != null) {
                handler.onCompleted();
            }

            try {
                logRequestMessage();
            } catch (Exception e) {
                setError(e);
            }
        }

        @Override
        public void onError(Exception e) throws Exception {
            log.error("onError(): ", e);

            if (handler != null) {
                handler.onError(e);
            } else {
                throw e;
            }
        }

        @Override
        public void close() {
            handler.close();
        }
    }

    private class DefaultSoapMessageHandler
            implements SoapMessageDecoder.Callback {

        @Override
        public void soap(SoapMessage message) throws Exception {
            if (request == null) {
                request = new ProxyMessageEncoder(reqOuts, getHashAlgoId());
                outputContentType = request.getContentType();
            }

            // We have the request SOAP message, we can start sending the
            // request to server proxy.
            continueProcessing();

            // In SSL mode, we need to send the OCSP response of our SSL cert.
            if (SystemProperties.isSslEnabled()) {
                writeOcspResponses();
            }

            request.soap(requestSoap);
        }

        @Override
        public void attachment(String contentType, InputStream content,
                Map<String, String> additionalHeaders) throws Exception {
            request.attachment(contentType, content, additionalHeaders);
        }

        @Override
        public void fault(SoapFault fault) throws Exception {
            onError(fault.toCodedException());
        }

        @Override
        public void onCompleted() {
            try {
                request.sign(KeyConf.getSigningCtx(requestSoap.getClient()));
            } catch (Exception ex) {
                setError(ex);
            }
        }

        @Override
        public void onError(Exception e) throws Exception {
            // Simply re-throw
            throw e;
        }

        private void writeOcspResponses() throws Exception {
            CertChain chain = KeyConf.getAuthKey().getCertChain();
            List<OCSPResp> ocspResponses = KeyConf.getAllOcspResponses(
                    chain.getAllCertsWithoutTrustedRoot()); // exclude TopCA
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

    private class AsyncSoapMessageHandler
            implements SoapMessageDecoder.Callback {

        WritingCtx writingCtx = null;

        SoapMessageConsumer consumer = null;

        @Override
        public void soap(SoapMessage message) throws Exception {
            if (writingCtx == null) {
                MessageQueue queue =
                        AsyncDB.getMessageQueue(requestServiceId.getClientId());
                writingCtx = queue.startWriting();
                consumer = writingCtx.getConsumer();
            }

            consumer.soap(message);
        }

        @Override
        public void attachment(String contentType, InputStream content,
                Map<String, String> additionalHeaders) throws Exception {
            if (consumer != null) {
                consumer.attachment(contentType, content, additionalHeaders);
            }
        }

        @Override
        public void fault(SoapFault fault) throws Exception {
            onError(fault.toCodedException());
        }

        @Override
        public void onCompleted() {
            try {
                commit();
                createAsyncSoapResponse();
            } catch (Exception e) {
                log.error("Error when committing async message", e);
                setError(e);
                rollback();
            } finally {
                continueProcessing();
            }
        }

        @Override
        public void onError(Exception e) {
            setError(e);
            try {
                rollback();
            } finally {
                continueProcessing();
            }
        }

        private void commit() throws Exception {
            if (writingCtx != null) {
                writingCtx.commit();
            }
        }

        private void rollback() {
            if (writingCtx != null) {
                try {
                    writingCtx.rollback();
                } catch (Exception e) {
                    log.error("Rollback failed", e);
                }
            }
        }

        private void createAsyncSoapResponse() {
            response = new ProxyMessage() {
                @Override
                public String getSoapContentType() {
                    return MimeTypes.TEXT_XML;
                }

                @Override
                public InputStream getSoapContent() throws Exception {
                    SoapMessageImpl soap = SoapUtils.toResponse(requestSoap);
                    return new ByteArrayInputStream(soap.getBytes());
                }
            };
        }
    }

    /**
     * Soap parser that changes the CentralServiceId to ServiceId in message
     * header.
     */
    private class RequestSoapParserImpl extends SoapParserImpl {

        @Override
        protected Soap createMessage(byte[] rawXml, SOAPMessage soap,
                String charset) throws Exception {
            if (soap.getSOAPHeader() != null) {
                SoapHeader header =
                        unmarshalHeader(SoapHeader.class, soap.getSOAPHeader());
                if (header.getCentralService() != null) {
                    if (header.getService() != null) {
                        throw new CodedException(X_MALFORMED_SOAP,
                                "Message header must contain either service id"
                                        + " or central service id");
                    }

                    ServiceId serviceId =
                            GlobalConf.getServiceId(header.getCentralService());
                    header.setService(serviceId);

                    SOAPEnvelope envelope = soap.getSOAPPart().getEnvelope();
                    envelope.removeChild(soap.getSOAPHeader());

                    Node soapBody = envelope.removeChild(soap.getSOAPBody());
                    envelope.removeContents(); // removes newlines etc.

                    Marshaller marshaller =
                            JaxbUtils.createMarshaller(SoapHeader.class,
                                    new SoapNamespacePrefixMapper());
                    marshaller.marshal(header, envelope);

                    envelope.appendChild(soapBody);

                    byte[] newRawXml = SoapUtils.getBytes(soap);
                    return super.createMessage(newRawXml, soap, charset);
                }
            }
            return super.createMessage(rawXml, soap, charset);
        }
    }
}
