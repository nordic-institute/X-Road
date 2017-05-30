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
package ee.ria.xroad.proxy.serverproxy;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.cert.CertChain;
import ee.ria.xroad.common.cert.CertHelper;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityCategoryId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.*;
import ee.ria.xroad.common.monitoring.MessageInfo;
import ee.ria.xroad.common.monitoring.MessageInfo.Origin;
import ee.ria.xroad.common.monitoring.MonitorAgent;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.HttpSender;
import ee.ria.xroad.proxy.conf.KeyConf;
import ee.ria.xroad.proxy.conf.SigningCtx;
import ee.ria.xroad.proxy.messagelog.MessageLog;
import ee.ria.xroad.proxy.protocol.ProxyMessage;
import ee.ria.xroad.proxy.protocol.ProxyMessageDecoder;
import ee.ria.xroad.proxy.protocol.ProxyMessageEncoder;
import ee.ria.xroad.proxy.util.MessageProcessorBase;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.HttpClient;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.Marshaller;
import javax.xml.soap.SOAPMessage;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static ee.ria.xroad.common.ErrorCodes.*;
import static ee.ria.xroad.common.util.AbstractHttpSender.CHUNKED_LENGTH;
import static ee.ria.xroad.common.util.CryptoUtils.*;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_HASH_ALGO_ID;

@Slf4j
class ServerMessageProcessor extends MessageProcessorBase {

    private static final String SERVERPROXY_SERVICE_HANDLERS =
            SystemProperties.PREFIX + "proxy.serverServiceHandlers";

    private final X509Certificate[] clientSslCerts;

    private final List<ServiceHandler> handlers = new ArrayList<>();

    private ProxyMessage requestMessage;
    private ServiceId requestServiceId;
    private SoapMessageImpl responseSoap;
    private SoapFault responseFault;

    private ProxyMessageDecoder decoder;
    private ProxyMessageEncoder encoder;

    private SigningCtx responseSigningCtx;

    ServerMessageProcessor(HttpServletRequest servletRequest,
            HttpServletResponse servletResponse, HttpClient httpClient,
            X509Certificate[] clientSslCerts) {
        super(servletRequest, servletResponse, httpClient);

        this.clientSslCerts = clientSslCerts;

        loadServiceHandlers();
    }

    @Override
    public void process() throws Exception {
        log.info("process({})", servletRequest.getContentType());
        try {
            readMessage();

            handleRequest();

            sign();

            close();

            postprocess();
        } catch (Exception ex) {
            handleException(ex);
        } finally {
            if (requestMessage != null) {
                requestMessage.consume();
            }
        }
    }

    @Override
    protected void preprocess() throws Exception {
        encoder = new ProxyMessageEncoder(servletResponse.getOutputStream(),
                getHashAlgoId());
        servletResponse.setContentType(encoder.getContentType());
        servletResponse.addHeader(HEADER_HASH_ALGO_ID, getHashAlgoId());
        if (SystemProperties.isServerAddCloseHeaderToSSResponse()) {
            servletResponse.addHeader("Connection", "close");
        }
    }

    @Override
    protected void postprocess() throws Exception {
        logResponseMessage();
    }

    private void loadServiceHandlers() {
        String serviceHandlerNames =
                System.getProperty(SERVERPROXY_SERVICE_HANDLERS);
        if (!StringUtils.isBlank(serviceHandlerNames)) {
            for (String serviceHandlerName : serviceHandlerNames.split(",")) {
                handlers.add(ServiceHandlerLoader.load(serviceHandlerName));

                log.debug("Loaded service handler: " + serviceHandlerName);
            }
        }

        handlers.add(new DefaultServiceHandlerImpl()); // default handler
    }

    private ServiceHandler getServiceHandler(ProxyMessage request) {
        for (ServiceHandler handler : handlers) {
            if (handler.canHandle(requestServiceId, request)) {
                return handler;
            }
        }

        return null;
    }

    private void handleRequest() throws Exception {
        ServiceHandler handler = getServiceHandler(requestMessage);
        if (handler == null) {
            handler = new DefaultServiceHandlerImpl();
        }

        if (handler.shouldVerifyAccess()) {
            verifyAccess();
        }

        if (handler.shouldVerifySignature()) {
            verifySignature();
        }

        if (handler.shouldLogSignature()) {
            logRequestMessage();
        }

        try {
            handler.startHandling();
            parseResponse(handler);
        } finally {
            handler.finishHandling();
        }
    }

    private void readMessage() throws Exception {
        log.trace("readMessage()");

        requestMessage = new ProxyMessage() {
            @Override
            public void soap(SoapMessageImpl soapMessage) throws Exception {
                super.soap(soapMessage);

                requestServiceId = soapMessage.getService();

                verifyClientStatus();

                responseSigningCtx =
                        KeyConf.getSigningCtx(requestServiceId.getClientId());

                if (SystemProperties.isSslEnabled()) {
                    verifySslClientCert();
                }
            }
        };

        decoder = new ProxyMessageDecoder(requestMessage,
                servletRequest.getContentType(), false,
                getHashAlgoId(servletRequest));
        try {
            decoder.parse(servletRequest.getInputStream());
        } catch (CodedException e) {
            throw e.withPrefix(X_SERVICE_FAILED_X);
        }

        // Check if the input contained all the required bits.
        checkRequest();
    }

    private void checkRequest() throws Exception {
        if (requestMessage.getSoap() == null) {
            throw new CodedException(X_MISSING_SOAP,
                    "Request does not have SOAP message");
        }

        if (requestMessage.getSignature() == null) {
            throw new CodedException(X_MISSING_SIGNATURE,
                    "Request does not have signature");
        }
    }

    private void verifyClientStatus() {
        ClientId client = requestServiceId.getClientId();

        String status = ServerConf.getMemberStatus(client);
        if (!ClientType.STATUS_REGISTERED.equals(status)) {
            throw new CodedException(X_UNKNOWN_MEMBER, "Client '%s' not found",
                    client);
        }
    }

    private void verifySslClientCert() throws Exception {
        log.trace("verifySslClientCert()");

        if (requestMessage.getOcspResponses().isEmpty()) {
            throw new CodedException(X_SSL_AUTH_FAILED,
                    "Cannot verify TLS certificate, corresponding "
                            + "OCSP response is missing");
        }

        String instanceIdentifier =
                requestMessage.getSoap().getClient().getXRoadInstance();

        X509Certificate trustAnchor =
                GlobalConf.getCaCert(instanceIdentifier,
                        clientSslCerts[clientSslCerts.length - 1]);
        if (trustAnchor == null) {
            throw new Exception("Unable to find trust anchor");
        }

        try {
            CertChain chain = CertChain.create(instanceIdentifier,
                    (X509Certificate[]) ArrayUtils.add(clientSslCerts,
                            trustAnchor));
            CertHelper.verifyAuthCert(chain,
                    requestMessage.getOcspResponses(),
                    requestMessage.getSoap().getClient());
        } catch (Exception e) {
            throw new CodedException(X_SSL_AUTH_FAILED, e);
        }
    }

    private void verifyAccess() throws Exception {
        log.trace("verifyAccess()");

        final SecurityServerId requestSecurityServer = requestMessage.getSoap().getSecurityServer();
        if (requestSecurityServer != null && !ServerConf.getIdentifier().equals(requestSecurityServer)) {
            throw new CodedException(X_INVALID_SECURITY_SERVER,
                    "Invalid security server %s", requestSecurityServer);
        }

        if (!ServerConf.serviceExists(requestServiceId)) {
            throw new CodedException(X_UNKNOWN_SERVICE,
                    "Unknown service: %s", requestServiceId);
        }

        verifySecurityCategory(requestServiceId);

        if (!ServerConf.isQueryAllowed(requestMessage.getSoap().getClient(),
                requestServiceId)) {
            throw new CodedException(X_ACCESS_DENIED,
                    "Request is not allowed: %s", requestServiceId);
        }

        String disabledNotice = ServerConf.getDisabledNotice(requestServiceId);
        if (disabledNotice != null) {
            throw new CodedException(X_SERVICE_DISABLED,
                    "Service %s is disabled: %s", requestServiceId,
                    disabledNotice);
        }
    }

    private void verifySecurityCategory(ServiceId service) throws Exception {
        Collection<SecurityCategoryId> required =
                ServerConf.getRequiredCategories(service);

        if (required == null || required.isEmpty()) {
            // Service requires nothing, we are satisfied.
            return;
        }

        Collection<SecurityCategoryId> provided =
                GlobalConf.getProvidedCategories(getClientAuthCert());

        for (SecurityCategoryId cat: required) {
            if (provided.contains(cat)) {
                return; // All OK.
            }
        }

        throw new CodedException(X_SECURITY_CATEGORY,
                "Service requires security categories (%s), "
                        + "but client only satisfies (%s)",
                StringUtils.join(required, ", "),
                StringUtils.join(provided, ", "));
    }

    private void verifySignature() throws Exception {
        log.trace("verifySignature()");

        decoder.verify(requestMessage.getSoap().getClient(),
                requestMessage.getSignature());
    }

    private void logRequestMessage() throws Exception {
        log.trace("logRequestMessage()");

        MessageLog.log(requestMessage.getSoap(), requestMessage.getSignature(),
                false);
    }

    private void logResponseMessage() throws Exception {
        if (responseSoap != null && encoder != null) {
            log.trace("logResponseMessage()");

            MessageLog.log(responseSoap, encoder.getSignature(), false);
        }
    }

    private void sendRequest(String serviceAddress, HttpSender httpSender)
            throws Exception {
        log.trace("sendRequest({})", serviceAddress);

        URI uri;
        try {
            uri = new URI(serviceAddress);
        } catch (URISyntaxException e) {
            throw new CodedException(X_SERVICE_MALFORMED_URL,
                    "Malformed service address '%s': %s", serviceAddress,
                    e.getMessage());
        }

        log.info("Sending request to {}", uri);
        try (InputStream in = requestMessage.getSoapContent()) {
            httpSender.doPost(uri, in, CHUNKED_LENGTH,
                    requestMessage.getSoapContentType());
        } catch (Exception ex) {
            throw translateException(ex).withPrefix(X_SERVICE_FAILED_X);
        }
    }

    private void parseResponse(ServiceHandler handler) throws Exception {
        log.trace("parseResponse()");

        preprocess();
        try {
            SoapMessageDecoder soapMessageDecoder =
                    new SoapMessageDecoder(handler.getResponseContentType(),
                            new SoapMessageHandler(),
                            new ResponseSoapParserImpl());
            soapMessageDecoder.parse(handler.getResponseContent());
        } catch (Exception ex) {
            throw translateException(ex).withPrefix(X_SERVICE_FAILED_X);
        }

        // If we received a fault from the service, we just send it back
        // to the client.
        if (responseFault != null) {
            throw responseFault.toCodedException();
        }

        // If we did not parse a response message (empty response
        // from server?), it is an error instead.
        if (responseSoap == null) {
            throw new CodedException(X_INVALID_MESSAGE,
                "No response message received from service").withPrefix(
                        X_SERVICE_FAILED_X);
        }
    }

    private void sign() throws Exception {
        log.trace("sign({})", requestServiceId.getClientId());

        encoder.sign(responseSigningCtx);
    }

    private void close() throws Exception {
        log.trace("close()");

        encoder.close();
    }

    private void handleException(Exception ex) throws Exception {
        CodedException exception;
        if (ex instanceof CodedException.Fault) {
            exception = (CodedException) ex;
        } else {
            exception = translateWithPrefix(SERVER_SERVERPROXY_X, ex);
        }

        if (encoder != null) {
            monitorAgentNotifyFailure(exception);

            encoder.fault(SoapFault.createFaultXml(exception));
            encoder.close();
        } else {
            throw ex;
        }
    }

    private void monitorAgentNotifyFailure(CodedException ex) {
        MessageInfo info = null;

        boolean requestIsComplete = requestMessage != null
                && requestMessage.getSoap() != null
                && requestMessage.getSignature() != null;

        // Include the request message only if the error was caused while
        // exchanging information with the adapter server.
        if (requestIsComplete && ex.getFaultCode().startsWith(
                SERVER_SERVERPROXY_X + "." + X_SERVICE_FAILED_X)) {
            info = createRequestMessageInfo();
        }

        MonitorAgent.failure(info, ex.getFaultCode(), ex.getFaultString());
    }

    @Override
    public MessageInfo createRequestMessageInfo() {
        if (requestMessage == null) {
            return null;
        }

        SoapMessageImpl soap = requestMessage.getSoap();
        return new MessageInfo(Origin.SERVER_PROXY, soap.getClient(),
                requestServiceId, soap.getUserId(), soap.getQueryId());
    }

    private X509Certificate getClientAuthCert() {
        return clientSslCerts != null ? clientSslCerts[0] : null;
    }

    private static String getHashAlgoId() {
        // TODO #2578 make hash function configurable
        return CryptoUtils.DEFAULT_DIGEST_ALGORITHM_ID;
    }

    private static String getHashAlgoId(HttpServletRequest servletRequest) {
        String hashAlgoId = servletRequest.getHeader(HEADER_HASH_ALGO_ID);
        if (hashAlgoId == null) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Could not get hash algorithm identifier from message");
        }

        return hashAlgoId;
    }

    private class DefaultServiceHandlerImpl implements ServiceHandler {

        private HttpSender sender;

        @Override
        public boolean shouldVerifyAccess() {
            return true;
        }

        @Override
        public boolean shouldVerifySignature() {
            return true;
        }

        @Override
        public boolean shouldLogSignature() {
            return true;
        }

        @Override
        public boolean canHandle(ServiceId requestSrvcId,
                ProxyMessage requestProxyMessage) {
            return true;
        }

        @Override
        public void startHandling() throws Exception {
            sender = createHttpSender();

            log.trace("processRequest({})", requestServiceId);

            String address = ServerConf.getServiceAddress(requestServiceId);
            if (address == null || address.isEmpty()) {
                throw new CodedException(X_SERVICE_MISSING_URL,
                        "Service address not specified for '%s'",
                        requestServiceId);
            }

            int timeout = ServerConf.getServiceTimeout(requestServiceId);

            sender.setTimeout((int) TimeUnit.SECONDS.toMillis(timeout));
            sender.setAttribute(ServiceId.class.getName(), requestServiceId);

            sender.addHeader("accept-encoding", "");

            sendRequest(address, sender);
        }

        @Override
        public void finishHandling() throws Exception {
            sender.close();
            sender = null;
        }

        @Override
        public String getResponseContentType() {
            return sender.getResponseContentType();
        }

        @Override
        public InputStream getResponseContent() {
            return sender.getResponseContent();
        }
    }

    private class SoapMessageHandler implements SoapMessageDecoder.Callback {
        @Override
        public void soap(SoapMessage message) throws Exception {
            responseSoap = (SoapMessageImpl) message;
            encoder.soap(responseSoap);
        }

        @Override
        public void attachment(String contentType, InputStream content,
                Map<String, String> additionalHeaders) throws Exception {
            encoder.attachment(contentType, content, additionalHeaders);
        }

        @Override
        public void fault(SoapFault fault) {
            responseFault = fault;
        }

        @Override
        public void onCompleted() {
            // Do nothing.
        }

        @Override
        public void onError(Exception t) throws Exception {
            throw t;
        }

        @Override
        public void close() {
            // Do nothing.
        }
    }

    /**
     * Soap parser that adds the request message hash to the response
     * message header.
     */
    private class ResponseSoapParserImpl extends SoapParserImpl {

        @Override
        protected Soap createMessage(byte[] rawXml, SOAPMessage soap,
                String charset) throws Exception {
            if (soap.getSOAPHeader() != null) {
                String hash = encodeBase64(calculateDigest(getHashAlgoId(),
                        requestMessage.getSoap().getBytes()));

                Marshaller m = JaxbUtils.createMarshaller(RequestHash.class);
                m.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
                m.marshal(new RequestHash(
                                getDigestAlgorithmURI(getHashAlgoId()), hash),
                        soap.getSOAPHeader());

                byte[] newRawXml = SoapUtils.getBytes(soap);
                return super.createMessage(newRawXml, soap, charset);
            } else {
                return super.createMessage(rawXml, soap, charset);
            }
        }
    }
}
