package ee.cyber.sdsb.proxy.clientproxy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.HttpClient;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.eclipse.jetty.http.MimeTypes;

import ee.cyber.sdsb.asyncdb.AsyncDB;
import ee.cyber.sdsb.asyncdb.WritingCtx;
import ee.cyber.sdsb.asyncdb.messagequeue.MessageQueue;
import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.cert.CertChain;
import ee.cyber.sdsb.common.conf.GlobalConf;
import ee.cyber.sdsb.common.conf.serverconf.ClientCert;
import ee.cyber.sdsb.common.conf.serverconf.IsAuthentication;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.ServiceId;
import ee.cyber.sdsb.common.message.SoapFault;
import ee.cyber.sdsb.common.message.SoapMessage;
import ee.cyber.sdsb.common.message.SoapMessageConsumer;
import ee.cyber.sdsb.common.message.SoapMessageDecoder;
import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.sdsb.common.message.SoapUtils;
import ee.cyber.sdsb.common.monitoring.MessageInfo;
import ee.cyber.sdsb.common.monitoring.MessageInfo.Origin;
import ee.cyber.sdsb.common.monitoring.MonitorAgent;
import ee.cyber.sdsb.common.util.CryptoUtils;
import ee.cyber.sdsb.common.util.HttpSender;
import ee.cyber.sdsb.common.util.MimeUtils;
import ee.cyber.sdsb.proxy.ProxyMain;
import ee.cyber.sdsb.proxy.conf.KeyConf;
import ee.cyber.sdsb.proxy.protocol.ProxyMessage;
import ee.cyber.sdsb.proxy.protocol.ProxyMessageDecoder;
import ee.cyber.sdsb.proxy.protocol.ProxyMessageEncoder;
import ee.cyber.sdsb.proxy.securelog.MessageLog;
import ee.cyber.sdsb.proxy.util.MessageProcessorBase;

import static ee.cyber.sdsb.common.ErrorCodes.*;
import static ee.cyber.sdsb.common.util.AbstractHttpSender.CHUNKED_LENGTH;
import static ee.cyber.sdsb.common.util.MimeUtils.HEADER_HASH_ALGO_ID;
import static ee.cyber.sdsb.common.util.MimeUtils.HEADER_PROXY_VERSION;
import static ee.cyber.sdsb.proxy.clientproxy.FastestConnectionSelectingSSLSocketFactory.ID_TARGETS;

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
     * Holds the client side SSL certificate.
     */
    private final ClientCert clientCert;

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

    /** Holds the response from server proxy. */
    private ProxyMessage response;

    ClientMessageProcessor(HttpServletRequest servletRequest,
            HttpServletResponse servletResponse, HttpClient httpClient,
            ClientCert clientCert) throws Exception {
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

        cacheConfigurationForCurrentThread();

        HandlerThread handlerThread = new HandlerThread();
        handlerThread.start();
        try {
            // Wait for the request SOAP message to be parsed before we can
            // start sending stuff.
            waitForSoapMessage();

            // If the handler thread excepted, do not continue.
            checkError();

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
            parseResponse(httpSender);
        }

        checkConsistency();

        logSignature();
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
            URI[] addresses = getServiceAddresses(requestServiceId);
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

            // Check for any errors from the handler thread once more.
            checkError();
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
    }

    private void logSignature() throws Exception {
        MessageLog.log(response.getSoap(), response.getSignature());
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
            Thread.currentThread().interrupt();
        }
    }

    private void continueProcessing() {
        log.trace("continueProcessing()");
        requestHandlerGate.countDown();
    }

    private void checkError() throws Exception {
        if (executionException != null) {
            log.error("checkError(): ", executionException);
            throw executionException;
        }
    }

    private void setError(Exception ex) {
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
        boolean sslEnabled = SystemProperties.isSslEnabled();
        if (!sslEnabled) {
            // In non-ssl mode we just connect to the first address
            return addresses[0];
        }

        String protocol = sslEnabled ? "https" : "http";
        int port = SystemProperties.getServerProxyPort();
        return new URI(protocol, null, "localhost", port, "/", null, null);
    }

    private static URI[] getServiceAddresses(ServiceId serviceProvider)
            throws Exception {
        log.trace("getServiceAddresses({})", serviceProvider);

        Collection<String> hostNames =
                GlobalConf.getProviderAddress(serviceProvider.getClientId());
        if (hostNames == null || hostNames.isEmpty()) {
            throw new CodedException(X_UNKNOWN_MEMBER,
                    "Could not find addresses for service provider \"%s\"",
                    serviceProvider);
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
        // TODO: #2578 make hash function configurable
        return CryptoUtils.DEFAULT_DIGEST_ALGORITHM_ID;
    }

    private static String getHashAlgoId(HttpSender httpSender) {
        return httpSender.getResponseHeaders().get(HEADER_HASH_ALGO_ID);
    }

    private class HandlerThread extends Thread {
        @Override
        public void run() {
            try {
                SoapMessageDecoder soapMessageDecoder =
                        new SoapMessageDecoder(servletRequest.getContentType(),
                                new SoapMessageHandler());
                try {
                    soapMessageDecoder.parse(servletRequest.getInputStream());
                } catch (Exception ex) {
                    throw new ClientException(translateException(ex));
                }
            } catch (Exception ex) {
                setError(ex);
            } finally {
                continueProcessing();
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
            requestServiceId =
                    GlobalConf.getServiceId(requestSoap.getService());

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
    }

    private class DefaultSoapMessageHandler
            implements SoapMessageDecoder.Callback {

        private ProxyMessageEncoder encoder;

        @Override
        public void soap(SoapMessage message) throws Exception {
            if (encoder == null) {
                encoder = new ProxyMessageEncoder(reqOuts, getHashAlgoId());
                outputContentType = encoder.getContentType();
            }

            // We have the request SOAP message, we can start sending the
            // request to server proxy.
            continueProcessing();

            // In SSL mode, we need to send the OCSP response of our SSL cert.
            if (SystemProperties.isSslEnabled()) {
                writeOcspResponses();
            }

            encoder.soap(requestSoap);
        }

        @Override
        public void attachment(String contentType, InputStream content,
                Map<String, String> additionalHeaders) throws Exception {
            encoder.attachment(contentType, content, additionalHeaders);
        }

        @Override
        public void fault(SoapFault fault) throws Exception {
            onError(fault.toCodedException());
        }

        @Override
        public void onCompleted() {
            try {
                encoder.sign(KeyConf.getSigningCtx(requestSoap.getClient()));
            } catch (Exception ex) {
                setError(ex);
            }

            if (encoder != null) {
                try {
                    encoder.close();
                } catch (Exception e) {
                    setError(e);
                }
            }
        }

        @Override
        public void onError(Exception e) throws Exception {
            if (encoder != null) {
                encoder.close();
            }

            // Simply re-throw
            throw e;
        }

        private void writeOcspResponses() throws Exception {
            CertChain chain = KeyConf.getAuthKey().getCertChain();
            List<OCSPResp> ocspResponses = KeyConf.getAllOcspResponses(
                    chain.getAllCertsWithoutTrustedRoot()); // exclude TopCA
            for (OCSPResp ocsp : ocspResponses) {
                encoder.ocspResponse(ocsp);
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
                    return new ByteArrayInputStream(soap.getXml().getBytes(
                                    StandardCharsets.UTF_8));
                }
            };
        }
    }
}
