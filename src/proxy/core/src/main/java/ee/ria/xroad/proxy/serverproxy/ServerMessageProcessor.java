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
package ee.ria.xroad.proxy.serverproxy;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.cert.CertChain;
import ee.ria.xroad.common.cert.CertChainFactory;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.DescriptionType;
import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.ResponseSoapParserImpl;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapMessage;
import ee.ria.xroad.common.message.SoapMessageDecoder;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapUtils;
import ee.ria.xroad.common.opmonitoring.OpMonitoringData;
import ee.ria.xroad.common.util.HttpSender;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;
import ee.ria.xroad.common.util.TimeUtils;
import ee.ria.xroad.proxy.conf.KeyConfProvider;
import ee.ria.xroad.proxy.conf.SigningCtx;
import ee.ria.xroad.proxy.conf.SigningCtxProvider;
import ee.ria.xroad.proxy.messagelog.MessageLog;
import ee.ria.xroad.proxy.protocol.ProxyMessage;
import ee.ria.xroad.proxy.protocol.ProxyMessageDecoder;
import ee.ria.xroad.proxy.protocol.ProxyMessageEncoder;
import ee.ria.xroad.proxy.util.MessageProcessorBase;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.HttpClient;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static ee.ria.xroad.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.X_ACCESS_DENIED;
import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_MESSAGE;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_SECURITY_SERVER;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_SERVICE_TYPE;
import static ee.ria.xroad.common.ErrorCodes.X_MISSING_SIGNATURE;
import static ee.ria.xroad.common.ErrorCodes.X_MISSING_SOAP;
import static ee.ria.xroad.common.ErrorCodes.X_SERVICE_DISABLED;
import static ee.ria.xroad.common.ErrorCodes.X_SERVICE_FAILED_X;
import static ee.ria.xroad.common.ErrorCodes.X_SERVICE_MALFORMED_URL;
import static ee.ria.xroad.common.ErrorCodes.X_SERVICE_MISSING_URL;
import static ee.ria.xroad.common.ErrorCodes.X_SSL_AUTH_FAILED;
import static ee.ria.xroad.common.ErrorCodes.X_UNKNOWN_MEMBER;
import static ee.ria.xroad.common.ErrorCodes.X_UNKNOWN_SERVICE;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.ErrorCodes.translateWithPrefix;
import static ee.ria.xroad.common.util.AbstractHttpSender.CHUNKED_LENGTH;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_HASH_ALGO_ID;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_ORIGINAL_CONTENT_TYPE;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_ORIGINAL_SOAP_ACTION;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_REQUEST_ID;
import static ee.ria.xroad.common.util.TimeUtils.getEpochMillisecond;

@Slf4j
class ServerMessageProcessor extends MessageProcessorBase {

    private static final String SERVERPROXY_SERVICE_HANDLERS = SystemProperties.PREFIX + "proxy.serverServiceHandlers";

    private final X509Certificate[] clientSslCerts;

    private final List<ServiceHandler> handlers = new ArrayList<>();

    private String originalSoapAction;
    private ProxyMessage requestMessage;
    private ServiceId requestServiceId;
    private SoapMessageImpl responseSoap;
    private SoapFault responseFault;
    private String xRequestId;

    private ProxyMessageDecoder decoder;
    private ProxyMessageEncoder encoder;

    private SigningCtx responseSigningCtx;

    private HttpClient opMonitorHttpClient;
    private OpMonitoringData opMonitoringData;

    ServerMessageProcessor(GlobalConfProvider globalConfProvider,
                           KeyConfProvider keyConfProvider,
                           ServerConfProvider serverConfProvider,
                           CertChainFactory certChainFactory,
                           RequestWrapper request, ResponseWrapper response,
                           HttpClient httpClient, X509Certificate[] clientSslCerts,
                           HttpClient opMonitorHttpClient, OpMonitoringData opMonitoringData) {
        super(globalConfProvider, keyConfProvider, serverConfProvider, certChainFactory, request, response, httpClient);

        this.clientSslCerts = clientSslCerts;
        this.opMonitorHttpClient = opMonitorHttpClient;
        this.opMonitoringData = opMonitoringData;

        loadServiceHandlers();
    }

    @Override
    @WithSpan
    public void process() throws Exception {
        log.info("process({})", jRequest.getContentType());

        xRequestId = jRequest.getHeaders().get(HEADER_REQUEST_ID);

        opMonitoringData.setXRequestId(xRequestId);
        updateOpMonitoringClientSecurityServerAddress();
        updateOpMonitoringServiceSecurityServerAddress();

        try {
            readMessage();

            handleRequest();

            sign();
            logResponseMessage();
            writeSignature();

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
    public boolean verifyMessageExchangeSucceeded() {
        return responseSoap != null && responseFault == null;
    }

    private void updateOpMonitoringClientSecurityServerAddress() {
        try {
            X509Certificate authCert = getClientAuthCert();

            if (authCert != null) {
                opMonitoringData.setClientSecurityServerAddress(globalConfProvider.getSecurityServerAddress(
                        globalConfProvider.getServerId(authCert)));
            }
        } catch (Exception e) {
            log.error("Failed to assign operational monitoring data field {}",
                    OpMonitoringData.CLIENT_SECURITY_SERVER_ADDRESS, e);
        }
    }

    private void updateOpMonitoringServiceSecurityServerAddress() {
        try {
            opMonitoringData.setServiceSecurityServerAddress(getSecurityServerAddress());
        } catch (Exception e) {
            log.error("Failed to assign operational monitoring data field {}",
                    OpMonitoringData.SERVICE_SECURITY_SERVER_ADDRESS, e);
        }
    }

    @Override
    protected void preprocess() {
        encoder = new ProxyMessageEncoder(jResponse.getOutputStream(), SoapUtils.getHashAlgoId());

        jResponse.setContentType(encoder.getContentType());
        jResponse.addHeader(HEADER_HASH_ALGO_ID, SoapUtils.getHashAlgoId().name());
    }

    @Override
    protected void postprocess() throws Exception {
        opMonitoringData.setSucceeded(true);
    }

    private void loadServiceHandlers() {
        String serviceHandlerNames = System.getProperty(SERVERPROXY_SERVICE_HANDLERS);

        if (!StringUtils.isBlank(serviceHandlerNames)) {
            for (String serviceHandlerName : serviceHandlerNames.split(",")) {
                handlers.add(ServiceHandlerLoader.load(serviceHandlerName, serverConfProvider, globalConfProvider));

                log.debug("Loaded service handler: {}", serviceHandlerName);
            }
        }

        handlers.add(new DefaultServiceHandlerImpl(serverConfProvider, globalConfProvider)); // default handler
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
            handler = new DefaultServiceHandlerImpl(serverConfProvider, globalConfProvider);
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
            handler.startHandling(jRequest, requestMessage, opMonitorHttpClient, opMonitoringData);
            parseResponse(handler);
        } finally {
            handler.finishHandling();
        }
    }

    private void readMessage() throws Exception {
        log.trace("readMessage()");

        originalSoapAction = validateSoapActionHeader(jRequest.getHeaders().get(HEADER_ORIGINAL_SOAP_ACTION));
        requestMessage = new ProxyMessage(jRequest.getHeaders().get(HEADER_ORIGINAL_CONTENT_TYPE)) {
            @Override
            public void soap(SoapMessageImpl soapMessage, Map<String, String> additionalHeaders) throws Exception {
                super.soap(soapMessage, additionalHeaders);

                updateOpMonitoringDataBySoapMessage(opMonitoringData, soapMessage);

                requestServiceId = soapMessage.getService();

                verifySecurityServer();
                verifyClientStatus();

                responseSigningCtx = SigningCtxProvider.getSigningCtx(requestServiceId.getClientId(), globalConfProvider, keyConfProvider);

                if (SystemProperties.isSslEnabled()) {
                    verifySslClientCert();
                }
            }
        };

        decoder = new ProxyMessageDecoder(globalConfProvider, requestMessage, jRequest.getContentType(), false,
                getHashAlgoId(jRequest));
        try {
            decoder.parse(jRequest.getInputStream());
        } catch (CodedException e) {
            throw e.withPrefix(X_SERVICE_FAILED_X);
        }

        updateOpMonitoringDataByRequest();

        // Check if the input contained all the required bits.
        checkRequest();
    }

    private void updateOpMonitoringDataByRequest() {
        if (requestMessage.getSoap() != null) {
            opMonitoringData.setRequestAttachmentCount(decoder.getAttachmentCount());

            if (decoder.getAttachmentCount() > 0) {
                opMonitoringData.setRequestMimeSize(requestMessage.getSoap().getBytes().length
                        + decoder.getAttachmentsByteCount());
            }
        }
    }

    private void checkRequest() {
        if (requestMessage.getSoap() == null) {
            throw new CodedException(X_MISSING_SOAP, "Request does not have SOAP message");
        }

        if (requestMessage.getSignature() == null) {
            throw new CodedException(X_MISSING_SIGNATURE, "Request does not have signature");
        }
        checkIdentifier(requestMessage.getSoap().getClient());
        checkIdentifier(requestMessage.getSoap().getService());
        checkIdentifier(requestMessage.getSoap().getSecurityServer());
    }

    private void verifyClientStatus() {
        ClientId client = requestServiceId.getClientId();

        String status = serverConfProvider.getMemberStatus(client);

        if (!ClientType.STATUS_REGISTERED.equals(status)) {
            throw new CodedException(X_UNKNOWN_MEMBER, "Client '%s' not found", client);
        }
    }

    private void verifySslClientCert() throws Exception {
        log.trace("verifySslClientCert()");

        if (requestMessage.getOcspResponses().isEmpty()) {
            throw new CodedException(X_SSL_AUTH_FAILED,
                    "Cannot verify TLS certificate, corresponding OCSP response is missing");
        }

        String instanceIdentifier = requestMessage.getSoap().getClient().getXRoadInstance();

        X509Certificate trustAnchor = globalConfProvider.getCaCert(instanceIdentifier,
                clientSslCerts[clientSslCerts.length - 1]);

        if (trustAnchor == null) {
            throw new Exception("Unable to find trust anchor");
        }

        try {
            CertChain chain = certChainFactory.create(instanceIdentifier, ArrayUtils.add(clientSslCerts, trustAnchor));
            certHelper.verifyAuthCert(chain, requestMessage.getOcspResponses(), requestMessage.getSoap().getClient());
        } catch (Exception e) {
            throw new CodedException(X_SSL_AUTH_FAILED, e);
        }
    }

    private void verifySecurityServer() throws Exception {
        final SecurityServerId requestServerId = requestMessage.getSoap().getSecurityServer();

        if (requestServerId != null) {
            final SecurityServerId serverId = serverConfProvider.getIdentifier();

            if (!requestServerId.equals(serverId)) {
                throw new CodedException(X_INVALID_SECURITY_SERVER,
                        "Invalid security server identifier '%s' expected '%s'", requestServerId, serverId);
            }
        }
    }

    private void verifyAccess() throws Exception {
        log.trace("verifyAccess()");

        if (!serverConfProvider.serviceExists(requestServiceId)) {
            throw new CodedException(X_UNKNOWN_SERVICE, "Unknown service: %s", requestServiceId);
        }

        DescriptionType descriptionType = serverConfProvider.getDescriptionType(requestServiceId);
        if (descriptionType != null && descriptionType != DescriptionType.WSDL) {
            throw new CodedException(X_INVALID_SERVICE_TYPE,
                    "Service is a REST service and cannot be called using SOAP interface");
        }

        if (!serverConfProvider.isQueryAllowed(requestMessage.getSoap().getClient(), requestServiceId)) {
            throw new CodedException(X_ACCESS_DENIED, "Request is not allowed: %s", requestServiceId);
        }

        String disabledNotice = serverConfProvider.getDisabledNotice(requestServiceId);

        if (disabledNotice != null) {
            throw new CodedException(X_SERVICE_DISABLED, "Service %s is disabled: %s", requestServiceId,
                    disabledNotice);
        }
    }

    private void verifySignature() throws Exception {
        log.trace("verifySignature()");

        decoder.verify(requestMessage.getSoap().getClient(), requestMessage.getSignature());
    }

    private void logRequestMessage() throws Exception {
        log.trace("logRequestMessage()");

        MessageLog.log(requestMessage.getSoap(), requestMessage.getSignature(), false, xRequestId);
    }

    private void logResponseMessage() throws Exception {
        if (responseSoap != null && encoder != null) {
            log.trace("logResponseMessage()");

            MessageLog.log(responseSoap, encoder.getSignature(), false, xRequestId);
        }
    }

    private void sendRequest(String serviceAddress, HttpSender httpSender) throws Exception {
        log.trace("sendRequest({})", serviceAddress);

        URI uri;
        try {
            uri = new URI(serviceAddress);
        } catch (URISyntaxException e) {
            throw new CodedException(X_SERVICE_MALFORMED_URL, "Malformed service address '%s': %s", serviceAddress,
                    e.getMessage());
        }

        log.info("Sending request to {}", uri);
        try (InputStream in = requestMessage.getSoapContent()) {
            opMonitoringData.setRequestOutTs(getEpochMillisecond());
            httpSender.doPost(uri, in, CHUNKED_LENGTH, jRequest.getHeaders().get(HEADER_ORIGINAL_CONTENT_TYPE));
            opMonitoringData.setResponseInTs(getEpochMillisecond());
        } catch (Exception ex) {
            if (ex instanceof CodedException) {
                opMonitoringData.setResponseInTs(getEpochMillisecond());
            }

            throw translateException(ex).withPrefix(X_SERVICE_FAILED_X);
        }
    }

    private void parseResponse(ServiceHandler handler) throws Exception {
        log.trace("parseResponse()");

        preprocess();

        // Preserve the original content type of the service response
        jResponse.addHeader(HEADER_ORIGINAL_CONTENT_TYPE, handler.getResponseContentType());

        try (SoapMessageHandler messageHandler = new SoapMessageHandler()) {
            SoapMessageDecoder soapMessageDecoder = new SoapMessageDecoder(handler.getResponseContentType(),
                    messageHandler, new ResponseSoapParserImpl(requestMessage.getSoap().getHash()));
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
            throw new CodedException(X_INVALID_MESSAGE, "No response message received from service").withPrefix(
                    X_SERVICE_FAILED_X);
        }

        updateOpMonitoringDataByResponse();
    }

    private void updateOpMonitoringDataByResponse() {
        opMonitoringData.setResponseAttachmentCount(encoder.getAttachmentCount());

        if (encoder.getAttachmentCount() > 0) {
            opMonitoringData.setResponseMimeSize(responseSoap.getBytes().length + encoder.getAttachmentsByteCount());
        }
    }

    private void sign() throws Exception {
        log.trace("sign({})", requestServiceId.getClientId());

        encoder.sign(responseSigningCtx);
    }

    private void writeSignature() throws Exception {
        log.trace("writeSignature()");

        encoder.writeSignature();
    }

    private void close() throws Exception {
        log.trace("close()");

        encoder.close();
    }

    private void handleException(Exception ex) throws Exception {
        if (encoder != null) {
            CodedException exception;

            if (ex instanceof CodedException.Fault) {
                exception = (CodedException.Fault) ex;
            } else {
                exception = translateWithPrefix(SERVER_SERVERPROXY_X, ex);
            }

            opMonitoringData.setFaultCodeAndString(exception);
            opMonitoringData.setResponseOutTs(getEpochMillisecond(), false);

            encoder.fault(SoapFault.createFaultXml(exception));
            encoder.close();
        } else {
            throw ex;
        }
    }

    private X509Certificate getClientAuthCert() {
        return clientSslCerts != null ? clientSslCerts[0] : null;
    }

    private static DigestAlgorithm getHashAlgoId(RequestWrapper request) {
        String hashAlgoId = request.getHeaders().get(HEADER_HASH_ALGO_ID);

        if (hashAlgoId == null) {
            throw new CodedException(X_INTERNAL_ERROR, "Could not get hash algorithm identifier from message");
        }

        return DigestAlgorithm.ofName(hashAlgoId);
    }

    private final class DefaultServiceHandlerImpl extends AbstractServiceHandler {

        private HttpSender sender;

        DefaultServiceHandlerImpl(ServerConfProvider serverConfProvider, GlobalConfProvider globalConfProvider) {
            super(serverConfProvider, globalConfProvider);
        }

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
        public boolean canHandle(ServiceId requestSrvcId, ProxyMessage requestProxyMessage) {
            return true;
        }

        @Override
        public void startHandling(RequestWrapper request, ProxyMessage proxyRequestMessage,
                                  HttpClient opMonitorClient, OpMonitoringData monitoringData) throws Exception {
            sender = createHttpSender();

            log.trace("processRequest({})", requestServiceId);

            String address = serverConfProvider.getServiceAddress(requestServiceId);

            if (address == null || address.isEmpty()) {
                throw new CodedException(X_SERVICE_MISSING_URL, "Service address not specified for '%s'",
                        requestServiceId);
            }

            int timeout = TimeUtils.secondsToMillis(serverConfProvider.getServiceTimeout(requestServiceId));

            sender.setConnectionTimeout(timeout);
            sender.setSocketTimeout(timeout);
            sender.setAttribute(ServiceId.class.getName(), requestServiceId);

            sender.addHeader("accept-encoding", "");
            sender.addHeader("SOAPAction", originalSoapAction);
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

    private final class SoapMessageHandler implements SoapMessageDecoder.Callback {
        @Override
        public void soap(SoapMessage message, Map<String, String> headers) throws Exception {
            responseSoap = (SoapMessageImpl) message;

            opMonitoringData.setResponseSize(responseSoap.getBytes().length);
            opMonitoringData.setResponseOutTs(getEpochMillisecond(), true);

            encoder.soap(responseSoap, headers);
        }

        @Override
        public void attachment(String contentType, InputStream content, Map<String, String> additionalHeaders)
                throws Exception {
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

}
