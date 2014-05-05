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

import org.apache.commons.io.IOUtils;
import org.apache.http.client.HttpClient;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.eclipse.jetty.http.MimeTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.asyncdb.AsyncDB;
import ee.cyber.sdsb.asyncdb.MessageQueue;
import ee.cyber.sdsb.asyncdb.WritingCtx;
import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.conf.GlobalConf;
import ee.cyber.sdsb.common.identifier.ServiceId;
import ee.cyber.sdsb.common.message.SoapMessage;
import ee.cyber.sdsb.common.message.SoapMessageConsumer;
import ee.cyber.sdsb.common.message.SoapMessageDecoder;
import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.sdsb.common.message.SoapUtils;
import ee.cyber.sdsb.common.monitoring.MessageInfo;
import ee.cyber.sdsb.common.monitoring.MessageInfo.Origin;
import ee.cyber.sdsb.common.monitoring.MonitorAgent;
import ee.cyber.sdsb.common.util.HttpSender;
import ee.cyber.sdsb.common.util.MimeUtils;
import ee.cyber.sdsb.proxy.conf.AuthKeyManager;
import ee.cyber.sdsb.proxy.conf.KeyConf;
import ee.cyber.sdsb.proxy.conf.ServerConf;
import ee.cyber.sdsb.proxy.protocol.ProxyMessage;
import ee.cyber.sdsb.proxy.protocol.ProxyMessageDecoder;
import ee.cyber.sdsb.proxy.protocol.ProxyMessageEncoder;
import ee.cyber.sdsb.proxy.securelog.SecureLog;
import ee.cyber.sdsb.proxy.util.MessageProcessorBase;

import static ee.cyber.sdsb.common.ErrorCodes.*;
import static ee.cyber.sdsb.proxy.clientproxy.FastestConnectionSelectingSSLSocketFactory.ID_TARGETS;

class ClientMessageProcessor extends MessageProcessorBase {

    private static final Logger LOG =
            LoggerFactory.getLogger(ClientMessageProcessor.class);

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
            HttpServletResponse servletResponse, HttpClient httpClient)
            throws Exception {
        super(servletRequest, servletResponse, httpClient);

        reqIns = new PipedInputStream();
        reqOuts = new PipedOutputStream(reqIns);
    }

    SoapMessageImpl getRequestSoap() {
        return requestSoap;
    }

    @Override
    protected void process() throws Exception {
        LOG.trace("process()");

        cacheConfigurationForCurrentThread();

        HandlerThread handlerThread = new HandlerThread();
        handlerThread.start();
        try {
            // Wait for the request SOAP message to be parsed before we can
            // start sending stuff.
            waitForSoapMessage();

            // If the handler thread excepted, do not continue.
            checkError();

            // If the message is synchronous, start sending proxy message
            if (!isAsync) {
                processRequest();
            }

            if (response != null) {
                sendResponse();
            }
        } catch (Exception e) {
            // Let's interrupt the handler thread so that it won't
            // block forever waiting for us to do something.
            handlerThread.interrupt();
            throw e;
        } finally {
            handlerThread.join();
        }

        onSuccess(requestSoap);
    }

    private void processRequest() throws Exception {
        LOG.trace("processRequest()");

        try (HttpSender httpSender = createHttpSender()) {
            sendRequest(httpSender);
            parseResponse(httpSender);
        }

        checkConsistency();

        logSignature();
    }

    private void sendRequest(HttpSender httpSender) throws Exception {
        LOG.trace("sendRequest()");
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

            try {
                httpSender.doPost(getDummyServiceAddress(addresses), reqIns,
                        outputContentType);
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
        LOG.trace("parseResponse()");
        response = new ProxyMessage();

        ProxyMessageDecoder decoder = new ProxyMessageDecoder(response,
                httpSender.getResponseContentType());
        try {
            decoder.parse(httpSender.getResponseContent());
        } catch (CodedException ex) {
            throw ex.withPrefix(X_SERVICE_FAILED_X);
        }

        // Ensure we have the required parts.
        checkResponse();

        decoder.verify(requestServiceId.getClientId(), response.getSignature(),
                GlobalConf.getVerificationCtx());
    }

    private void checkResponse() throws Exception {
        LOG.trace("checkResponse()");
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
        LOG.trace("checkConsistency()");
        if (!SoapUtils.checkConsistency(requestSoap, response.getSoap())) {
            LOG.error("Inconsistent request-response: {}\n{}",
                    requestSoap.getXml(), response.getSoap().getXml());
            // The error code includes ServiceFailed because it indicates
            // faulty response from service (problem on the other side).
            throw new CodedException(X_INCONSISTENT_RESPONSE,
                    "Response from server proxy is not consistent with request")
                    .withPrefix(X_SERVICE_FAILED_X);
        }
    }

    private void logSignature() throws Exception {
        SecureLog.logSignature(response.getSoap(), response.getSignature());
    }

    private void sendResponse() throws Exception {
        LOG.trace("sendResponse()");
        servletResponse.setStatus(HttpServletResponse.SC_OK);
        servletResponse.setHeader("SOAPAction", "");
        servletResponse.setCharacterEncoding(MimeUtils.UTF8);

        servletResponse.setContentType(response.getSoapContentType());
        try (InputStream is = response.getSoapContent()) {
            IOUtils.copy(is, servletResponse.getOutputStream());
        }
    }

    private void waitForSoapMessage() {
        LOG.trace("waitForSoapMessage()");
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
        LOG.trace("continueProcessing()");
        requestHandlerGate.countDown();
    }

    private void checkError() throws Exception {
        if (executionException != null) {
            LOG.error("checkError(): ", executionException);
            throw executionException;
        }
    }

    private void setError(Exception ex) {
        if (executionException == null) {
            executionException = translateException(ex);
        }
    }

    protected MessageInfo createRequestMessageInfo() {
        return new MessageInfo(Origin.CLIENT_PROXY, requestSoap.getClient(),
                requestServiceId, requestSoap.getUserId(),
                requestSoap.getQueryId());
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
        LOG.debug("getServiceAddresses({})", serviceProvider);

        Collection<String> hostNames = GlobalConf.getProviderAddress(
                serviceProvider.getClientId());
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
            LOG.trace("soap({})", message.getXml());
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
            LOG.trace("attachment({})", contentType);
            if (handler != null) {
                handler.attachment(contentType, content, additionalHeaders);
            } else {
                // Theoretically, should not happen
                throw new CodedException(X_INTERNAL_ERROR,
                        "No soap message handler present");
            }
        }

        private void chooseHandler() {
            isAsync = requestSoap.isAsync() && (servletRequest.getHeader(
                    SoapMessageImpl.X_IGNORE_ASYNC) == null);
            if (isAsync) {
                LOG.trace("Creating handler for asynchronous messages");
                handler = new AsyncSoapMessageHandler();
            } else {
                LOG.trace("Creating handler for normal messages");
                handler = new DefaultSoapMessageHandler();
            }
        }

        @Override
        public void onCompleted() {
            LOG.trace("onCompleted()");
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
            LOG.error("onError(): ", e);
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
                encoder = new ProxyMessageEncoder(reqOuts);
                outputContentType = encoder.getContentType();
            }

            // We have the request SOAP message, we can start sending the
            // request to server proxy.
            continueProcessing();

            // In SSL mode, we need to send the OCSP response of our SSL cert.
            if (SystemProperties.isSslEnabled()) {
                writeOcspResponse();
            }

            encoder.soap(requestSoap);
        }

        @Override
        public void attachment(String contentType, InputStream content,
                Map<String, String> additionalHeaders) throws Exception {
            encoder.attachment(contentType, content, additionalHeaders);
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

        private void writeOcspResponse() throws Exception {
            AuthKeyManager km = AuthKeyManager.getInstance();
            OCSPResp ocsp = ServerConf.getOcspResponse(km.getAuthCert());
            if (ocsp == null) { // no response was found
                throw new ClientException(X_SSL_AUTH_FAILED,
                        "Could not find OCSP response for SSL certificate");
            }

            encoder.ocspResponse(ocsp);
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
            } // handle else?
        }

        @Override
        public void onCompleted() {
            try {
                commit();
                createAsyncSoapResponse();
            } catch (Exception e) {
                LOG.error("Error when committing async message", e);
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
                    LOG.error("Rollback failed", e);
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
