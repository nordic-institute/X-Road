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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.cert.CertChain;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.IsAuthentication;
import ee.ria.xroad.common.conf.serverconf.IsAuthenticationData;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.identifier.CentralServiceId;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.RequestHash;
import ee.ria.xroad.common.message.SaxSoapParserImpl;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapHeader;
import ee.ria.xroad.common.message.SoapMessage;
import ee.ria.xroad.common.message.SoapMessageDecoder;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapUtils;
import ee.ria.xroad.common.monitoring.MessageInfo;
import ee.ria.xroad.common.monitoring.MessageInfo.Origin;
import ee.ria.xroad.common.monitoring.MonitorAgent;
import ee.ria.xroad.common.opmonitoring.OpMonitoringData;
import ee.ria.xroad.common.util.HttpSender;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.proxy.ProxyMain;
import ee.ria.xroad.proxy.conf.KeyConf;
import ee.ria.xroad.proxy.messagelog.MessageLog;
import ee.ria.xroad.proxy.protocol.ProxyMessage;
import ee.ria.xroad.proxy.protocol.ProxyMessageDecoder;
import ee.ria.xroad.proxy.protocol.ProxyMessageEncoder;
import ee.ria.xroad.proxy.util.MessageProcessorBase;
import lombok.EqualsAndHashCode;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.client.protocol.HttpClientContext;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.util.Arrays;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.AttributesImpl;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.namespace.QName;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Writer;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static ee.ria.xroad.common.ErrorCodes.*;
import static ee.ria.xroad.common.SystemProperties.getServerProxyPort;
import static ee.ria.xroad.common.SystemProperties.isSslEnabled;
import static ee.ria.xroad.common.util.AbstractHttpSender.CHUNKED_LENGTH;
import static ee.ria.xroad.common.util.CryptoUtils.decodeBase64;
import static ee.ria.xroad.common.util.CryptoUtils.encodeBase64;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_HASH_ALGO_ID;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_ORIGINAL_CONTENT_TYPE;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_ORIGINAL_SOAP_ACTION;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_PROXY_VERSION;
import static ee.ria.xroad.common.util.TimeUtils.getEpochMillisecond;
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

    /** Holds the response from server proxy. */
    private ProxyMessage response;

    //** Holds operational monitoring data. */
    private volatile OpMonitoringData opMonitoringData;

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
        super(servletRequest, servletResponse, httpClient);

        this.clientCert = clientCert;
        this.opMonitoringData = opMonitoringData;
        this.reqIns = new PipedInputStream();
        this.reqOuts = new PipedOutputStream(reqIns);
    }

    @Override
    public void process() throws Exception {
        log.trace("process()");

        updateOpMonitoringClientSecurityServerAddress();

        Future<?> soapHandler = SOAP_HANDLER_EXECUTOR.submit(this::handleSoap);

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

            processRequest();

            if (response != null) {
                sendResponse();
            }
        } catch (Exception e) {
            if (reqIns != null) {
                reqIns.close();
            }

            // Let's interrupt the handler thread so that it won't
            // block forever waiting for us to do something.
            soapHandler.cancel(true);

            throw e;
        } finally {
            if (response != null) {
                response.consume();
            }
        }
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
            // If we're using SSL, we need to include the provider name in
            // the HTTP request so that server proxy could verify the SSL
            // certificate properly.
            if (isSslEnabled()) {
                httpSender.setAttribute(AuthTrustVerifier.ID_PROVIDERNAME, requestServiceId);
            }

            // Start sending the request to server proxies. The underlying
            // SSLConnectionSocketFactory will select the fastest address
            // (socket that connects first) from the provided addresses.
            List<URI> tmp = getServiceAddresses(requestServiceId, requestSoap.getSecurityServer());
            Collections.shuffle(tmp);
            URI[] addresses = tmp.toArray(new URI[0]);

            updateOpMonitoringServiceSecurityServerAddress(addresses, httpSender);

            httpSender.setAttribute(ID_TARGETS, addresses);

            if (SystemProperties.isEnableClientProxyPooledConnectionReuse()) {
                // set the servers with this subsystem as the user token, this will pool the connections per groups of
                // security servers.
                httpSender.setAttribute(HttpClientContext.USER_TOKEN, new TargetHostsUserToken(addresses));
            }

            httpSender.setConnectionTimeout(SystemProperties.getClientProxyTimeout());
            httpSender.setSocketTimeout(SystemProperties.getClientProxyHttpClientTimeout());

            httpSender.addHeader(HEADER_HASH_ALGO_ID, SoapUtils.getHashAlgoId());
            httpSender.addHeader(HEADER_PROXY_VERSION, ProxyMain.getVersion());

            // Preserve the original content type in the "x-original-content-type"
            // HTTP header, which will be used to send the request to the
            // service provider
            httpSender.addHeader(HEADER_ORIGINAL_CONTENT_TYPE, servletRequest.getContentType());

            // Preserve the original SOAPAction header
            httpSender.addHeader(HEADER_ORIGINAL_SOAP_ACTION, originalSoapAction);

            try {
                opMonitoringData.setRequestOutTs(getEpochMillisecond());

                httpSender.doPost(addresses[0], reqIns, CHUNKED_LENGTH, outputContentType);

                opMonitoringData.setResponseInTs(getEpochMillisecond());
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

    @EqualsAndHashCode
    public static class TargetHostsUserToken {
        private final Set<URI> targetHosts;

        TargetHostsUserToken(Set<URI> targetHosts) {
            if (targetHosts != null) {
                this.targetHosts = targetHosts;
            } else {
                this.targetHosts = new HashSet<>();
            }
        }

        TargetHostsUserToken(URI[] uris) {
            if (uris == null || uris.length == 0) {
                this.targetHosts = Collections.emptySet();
            } else {
                if (uris.length == 1) {
                    this.targetHosts = Collections.singleton(uris[0]);
                } else {
                    this.targetHosts = new HashSet<>(java.util.Arrays.asList(uris));
                }
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

    private void updateOpMonitoringServiceSecurityServerAddress(URI addresses[], HttpSender httpSender) {
        if (addresses.length == 1) {
            opMonitoringData.setServiceSecurityServerAddress(addresses[0].getHost());
        } else {
            // In case multiple addresses the service security server
            // address will be founded by received TLS authentication
            // certificate in AuthTrustVerifier class.

            httpSender.setAttribute(OpMonitoringData.class.getName(), opMonitoringData);
        }
    }

    private void updateOpMonitoringDataByResponse(ProxyMessageDecoder decoder) {
        if (response.getSoap() != null) {
            long responseSoapSize = response.getSoap().getBytes().length;

            opMonitoringData.setResponseSoapSize(responseSoapSize);
            opMonitoringData.setResponseAttachmentCount(decoder.getAttachmentCount());

            if (decoder.getAttachmentCount() > 0) {
                opMonitoringData.setResponseMimeSize(responseSoapSize + decoder.getAttachmentsByteCount());
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

        MessageLog.log(response.getSoap(), response.getSignature(), true);
    }

    private void sendResponse() throws Exception {
        log.trace("sendResponse()");

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

    @Override
    public MessageInfo createRequestMessageInfo() {
        if (requestSoap == null) {
            return null;
        }

        return new MessageInfo(Origin.CLIENT_PROXY, requestSoap.getClient(), requestServiceId, requestSoap.getUserId(),
                requestSoap.getQueryId());
    }

    protected void verifyClientStatus() throws Exception {
        ClientId client = requestSoap.getClient();
        String status = ServerConf.getMemberStatus(client);

        if (!ClientType.STATUS_REGISTERED.equals(status)) {
            throw new CodedException(X_UNKNOWN_MEMBER, "Client '%s' not found", client);
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

    private static List<URI> getServiceAddresses(ServiceId serviceProvider, SecurityServerId serverId)
            throws Exception {
        log.trace("getServiceAddresses({}, {})", serviceProvider, serverId);

        Collection<String> hostNames = GlobalConf.getProviderAddress(serviceProvider.getClientId());

        if (hostNames == null || hostNames.isEmpty()) {
            throw new CodedException(X_UNKNOWN_MEMBER, "Could not find addresses for service provider \"%s\"",
                    serviceProvider);
        }

        if (serverId != null) {
            final String securityServerAddress = GlobalConf.getSecurityServerAddress(serverId);

            if (securityServerAddress == null) {
                throw new CodedException(X_INVALID_SECURITY_SERVER, "Could not find security server \"%s\"", serverId);
            }

            if (!hostNames.contains(securityServerAddress)) {
                throw new CodedException(X_INVALID_SECURITY_SERVER, "Invalid security server \"%s\"", serviceProvider);
            }

            hostNames = Collections.singleton(securityServerAddress);
        }

        String protocol = isSslEnabled() ? "https" : "http";
        int port = getServerProxyPort();

        List<URI> addresses = new ArrayList<>(hostNames.size());

        for (String host : hostNames) {
            addresses.add(new URI(protocol, null, host, port, "/", null, null));
        }

        return addresses;
    }

    private static String getHashAlgoId(HttpSender httpSender) {
        return httpSender.getResponseHeaders().get(HEADER_HASH_ALGO_ID);
    }

    public void handleSoap() {
        try (SoapMessageHandler handler = new SoapMessageHandler()) {
            SoapMessageDecoder soapMessageDecoder = new SoapMessageDecoder(servletRequest.getContentType(),
                    handler, new RequestSoapParserImpl());
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

            MessageLog.log(requestSoap, request.getSignature(), true);
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

    /**
     * Soap parser that changes the CentralServiceId to ServiceId in message
     * header.
     */
    private class RequestSoapParserImpl extends SaxSoapParserImpl {

        private ServiceId serviceId;

        private String nestedPrefix;

        private AttributesImpl wrapperElementAttributes;
        private Attributes nestedElementAttributes;

        private char[] nestedTabs;
        private char[] wrapperTabs;

        private boolean inServiceElement;
        private boolean inHeader;

        private SoapHeaderHandler headerHandler;

        // do not write processed XML beyond the header if not a central
        // service request, use raw request XML instead
        @Override
        protected boolean isProcessedXmlRequired() {
            boolean headerNotProcessed = headerHandler == null || !headerHandler.isFinished();

            return headerNotProcessed || headerHandler.getHeader().getCentralService() != null;
        }

        @Override
        protected void writeStartElementXml(String prefix, QName element, Attributes attributes, Writer writer) {
            if (inHeader && element.equals(QNAME_XROAD_CENTRAL_SERVICE)) {
                beginServiceElementSubstitution(attributes);
                inServiceElement = true;
            } else if (!inServiceElement) {
                super.writeStartElementXml(prefix, element, attributes, writer);
            }
        }

        @Override
        protected void writeEndElementXml(String prefix, QName element,
                Attributes attributes, Writer writer) {
            if (inHeader) {
                if (element.equals(QNAME_XROAD_CENTRAL_SERVICE)) {
                    if (serviceId != null) {
                        finishServiceElementSubstitution(prefix, writer);
                    }

                    inServiceElement = false;
                } else if (!inServiceElement) {
                    super.writeEndElementXml(prefix, element, attributes, writer);
                }

                if (inServiceElement && element.equals(QNAME_ID_SERVICE_CODE)) {
                    nestedPrefix = prefix;
                    nestedElementAttributes = attributes;
                }
            } else {
                super.writeEndElementXml(prefix, element, attributes, writer);
            }
        }

        @Override
        protected void writeCharactersXml(char[] characters, int start, int length, Writer writer) {
            if (inServiceElement) {
                String value = new String(characters, start, length);
                char[] chars = value.toCharArray();

                if (value.trim().isEmpty()) {
                    if (nestedTabs == null) {
                        nestedTabs = chars;
                    }

                    wrapperTabs = chars;
                }
            } else {
                super.writeCharactersXml(characters, start, length, writer);
            }
        }

        @Override
        protected SoapHeaderHandler getSoapHeaderHandler(SoapHeader header) {
            headerHandler = new SoapHeaderHandler(header) {
                @Override
                protected void openTag() {
                    super.openTag();
                    inHeader = true;
                }

                @Override
                protected void onCentralService(CentralServiceId centralServiceId) {
                    super.onCentralService(centralServiceId);
                    header.setCentralService(centralServiceId);
                    serviceId = GlobalConf.getServiceId(centralServiceId);
                    header.setService(serviceId);
                }

                @Override
                protected void closeTag() {
                    super.closeTag();
                    inHeader = false;
                }
            };

            return headerHandler;
        }

        private void beginServiceElementSubstitution(Attributes attributes) {
            wrapperElementAttributes = new AttributesImpl(attributes);

            for (int i = 0; i < wrapperElementAttributes.getLength(); i++) {
                if (wrapperElementAttributes.getValue(i).endsWith("CENTRALSERVICE")) {
                    wrapperElementAttributes.setValue(i, "SERVICE");

                    break;
                }
            }
        }

        private void finishServiceElementSubstitution(String prefix, Writer writer) {
            super.writeStartElementXml(prefix, QNAME_XROAD_SERVICE, wrapperElementAttributes, writer);

            writeElement(writer, QNAME_ID_INSTANCE, serviceId.getXRoadInstance());
            writeElement(writer, QNAME_ID_MEMBER_CLASS, serviceId.getMemberClass());
            writeElement(writer, QNAME_ID_MEMBER_CODE, serviceId.getMemberCode());

            if (serviceId.getSubsystemCode() != null) {
                String subsystemCode = serviceId.getSubsystemCode();
                writeElement(writer, QNAME_ID_SUBSYSTEM_CODE, subsystemCode);
            }

            writeElement(writer, QNAME_ID_SERVICE_CODE, serviceId.getServiceCode());

            if (serviceId.getServiceVersion() != null) {
                String serviceVersion = serviceId.getServiceVersion();
                writeElement(writer, QNAME_ID_SERVICE_VERSION, serviceVersion);
            }

            char[] tabs = wrapperTabs != null ? wrapperTabs : new char[0];
            super.writeCharactersXml(tabs, 0, tabs.length, writer);
            super.writeEndElementXml(prefix, QNAME_XROAD_SERVICE, wrapperElementAttributes, writer);
        }

        @SneakyThrows
        private void writeElement(Writer writer, QName element, String value) {
            char[] tabs = nestedTabs != null ? nestedTabs : new char[0];

            super.writeCharactersXml(tabs, 0, tabs.length, writer);
            super.writeStartElementXml(nestedPrefix, element, nestedElementAttributes, writer);
            super.writeCharactersXml(value.toCharArray(), 0, value.length(), writer);
            super.writeEndElementXml(nestedPrefix, element, nestedElementAttributes, writer);
        }
    }
}
