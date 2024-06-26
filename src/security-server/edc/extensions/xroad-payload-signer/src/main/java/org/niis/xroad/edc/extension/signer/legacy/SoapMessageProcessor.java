/*
 * The MIT License
 *
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

package org.niis.xroad.edc.extension.signer.legacy;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.DescriptionType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.ResponseSoapParserImpl;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapMessage;
import ee.ria.xroad.common.message.SoapMessageDecoder;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapUtils;
import ee.ria.xroad.common.messagelog.SoapLogMessage;
import ee.ria.xroad.common.util.CachingStream;
import ee.ria.xroad.common.util.HttpSender;
import ee.ria.xroad.common.util.TimeUtils;
import ee.ria.xroad.proxy.conf.SigningCtx;
import ee.ria.xroad.proxy.conf.SigningCtxProvider;
import ee.ria.xroad.proxy.protocol.ProxyMessage;
import ee.ria.xroad.proxy.protocol.ProxyMessageDecoder;
import ee.ria.xroad.proxy.protocol.ProxyMessageEncoder;

import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.apache.http.client.HttpClient;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.niis.xroad.edc.spi.messagelog.XRoadMessageLog;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import static ee.ria.xroad.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.X_ACCESS_DENIED;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_MESSAGE;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_SECURITY_SERVER;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_SERVICE_TYPE;
import static ee.ria.xroad.common.ErrorCodes.X_MISSING_SIGNATURE;
import static ee.ria.xroad.common.ErrorCodes.X_MISSING_SOAP;
import static ee.ria.xroad.common.ErrorCodes.X_SERVICE_DISABLED;
import static ee.ria.xroad.common.ErrorCodes.X_SERVICE_FAILED_X;
import static ee.ria.xroad.common.ErrorCodes.X_SERVICE_MALFORMED_URL;
import static ee.ria.xroad.common.ErrorCodes.X_SERVICE_MISSING_URL;
import static ee.ria.xroad.common.ErrorCodes.X_UNKNOWN_MEMBER;
import static ee.ria.xroad.common.ErrorCodes.X_UNKNOWN_SERVICE;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.ErrorCodes.translateWithPrefix;
import static ee.ria.xroad.common.util.AbstractHttpSender.CHUNKED_LENGTH;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_HASH_ALGO_ID;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_ORIGINAL_CONTENT_TYPE;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_ORIGINAL_SOAP_ACTION;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_REQUEST_ID;

public class SoapMessageProcessor extends MessageProcessorBase {

//    private final X509Certificate[] clientSslCerts;

    private String originalSoapAction;
    private ProxyMessage requestMessage;
    private ServiceId requestServiceId;
    private SoapMessageImpl responseSoap;
    private SoapFault responseFault;
    private String xRequestId;

    private ProxyMessageDecoder decoder;
    private ProxyMessageEncoder encoder;

    private SigningCtx responseSigningCtx;

    private XRoadMessageLog xRoadMessageLog;
    private Response.ResponseBuilder responseBuilder;

    private CachingStream cachingStream;

    public SoapMessageProcessor(ContainerRequestContext request, AsyncResponse response,
                                HttpClient httpClient, XRoadMessageLog messageLog, Monitor monitor) {
        super(request, response, httpClient, monitor);

//        this.clientSslCerts = clientSslCerts;
        this.xRoadMessageLog = messageLog;

        try {
            this.cachingStream = new CachingStream();
        } catch (IOException e) {
            throw new EdcException(e);
        }
    }

    @Override
    public void process() throws Exception {
        monitor.debug(() -> "process(%s)".formatted(jRequest.getMediaType().toString()));

        xRequestId = jRequest.getHeaderString(HEADER_REQUEST_ID);

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

    @Override
    protected void preprocess() {
        encoder = new ProxyMessageEncoder(cachingStream, SoapUtils.getHashAlgoId());

        responseBuilder = Response.ok()
                .type(encoder.getContentType())
                .header(HEADER_HASH_ALGO_ID, SoapUtils.getHashAlgoId());
    }

    @Override
    protected void postprocess() throws Exception {
    }

    private void handleRequest() throws Exception {
        DefaultServiceHandlerImpl handler = new DefaultServiceHandlerImpl();

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
            handler.startHandling(jRequest, requestMessage);
            parseResponse(handler);
        } finally {
            handler.finishHandling();
        }
    }

    private void readMessage() throws Exception {
        monitor.debug("readMessage()");

        originalSoapAction = validateSoapActionHeader(jRequest.getHeaderString(HEADER_ORIGINAL_SOAP_ACTION));
        requestMessage = new ProxyMessage(jRequest.getHeaderString(HEADER_ORIGINAL_CONTENT_TYPE)) {
            @Override
            public void soap(SoapMessageImpl soapMessage, Map<String, String> additionalHeaders) throws Exception {
                super.soap(soapMessage, additionalHeaders);

                requestServiceId = soapMessage.getService();

                verifySecurityServer();
                verifyClientStatus();

                responseSigningCtx = SigningCtxProvider.getSigningCtx(requestServiceId.getClientId());

//                if (SystemProperties.isSslEnabled()) {
//                    verifySslClientCert();
//                }
            }
        };

        decoder = new ProxyMessageDecoder(requestMessage, jRequest.getMediaType().toString(), false,
                getHashAlgoId(jRequest));
        try {
            decoder.parse(jRequest.getEntityStream());
        } catch (CodedException e) {
            throw e.withPrefix(X_SERVICE_FAILED_X);
        }

        // Check if the input contained all the required bits.
        checkRequest();
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

        String status = ServerConf.getMemberStatus(client);

        if (!ClientType.STATUS_REGISTERED.equals(status)) {
            throw new CodedException(X_UNKNOWN_MEMBER, "Client '%s' not found", client);
        }
    }

//    private void verifySslClientCert() throws Exception {
////        log.trace("verifySslClientCert()");
//
//        if (requestMessage.getOcspResponses().isEmpty()) {
//            throw new CodedException(X_SSL_AUTH_FAILED,
//                    "Cannot verify TLS certificate, corresponding OCSP response is missing");
//        }
//
//        String instanceIdentifier = requestMessage.getSoap().getClient().getXRoadInstance();
//
//        X509Certificate trustAnchor = GlobalConf.getCaCert(instanceIdentifier,
//                clientSslCerts[clientSslCerts.length - 1]);
//
//        if (trustAnchor == null) {
//            throw new Exception("Unable to find trust anchor");
//        }
//
//        try {
//            CertChain chain = CertChain.create(instanceIdentifier, (X509Certificate[]) ArrayUtils.add(clientSslCerts,
//                    trustAnchor));
//            CertHelper.verifyAuthCert(chain, requestMessage.getOcspResponses(), requestMessage.getSoap().getClient());
//        } catch (Exception e) {
//            throw new CodedException(X_SSL_AUTH_FAILED, e);
//        }
//    }

    private void verifySecurityServer() {
        final SecurityServerId requestServerId = requestMessage.getSoap().getSecurityServer();

        if (requestServerId != null) {
            final SecurityServerId serverId = ServerConf.getIdentifier();

            if (!requestServerId.equals(serverId)) {
                throw new CodedException(X_INVALID_SECURITY_SERVER,
                        "Invalid security server identifier '%s' expected '%s'", requestServerId, serverId);
            }
        }
    }

    private void verifyAccess() throws Exception {
       monitor.debug("verifyAccess()");

        if (!ServerConf.serviceExists(requestServiceId)) {
            throw new CodedException(X_UNKNOWN_SERVICE, "Unknown service: %s", requestServiceId);
        }

        DescriptionType descriptionType = ServerConf.getDescriptionType(requestServiceId);
        if (descriptionType != null && descriptionType != DescriptionType.WSDL) {
            throw new CodedException(X_INVALID_SERVICE_TYPE,
                    "Service is a REST service and cannot be called using SOAP interface");
        }

        if (!ServerConf.isQueryAllowed(requestMessage.getSoap().getClient(), requestServiceId)) {
            throw new CodedException(X_ACCESS_DENIED, "Request is not allowed: %s", requestServiceId);
        }

        String disabledNotice = ServerConf.getDisabledNotice(requestServiceId);

        if (disabledNotice != null) {
            throw new CodedException(X_SERVICE_DISABLED, "Service %s is disabled: %s", requestServiceId,
                    disabledNotice);
        }
    }

    private void verifySignature() throws Exception {
        monitor.debug("verifySignature()");

        decoder.verify(requestMessage.getSoap().getClient(), requestMessage.getSignature());
    }

    private void logRequestMessage() throws Exception {
        monitor.debug("logRequestMessage()");

        xRoadMessageLog.log(new SoapLogMessage(requestMessage.getSoap(),
                requestMessage.getSignature(), false, xRequestId));
    }

    private void logResponseMessage() throws Exception {
        if (responseSoap != null && encoder != null) {
            monitor.debug("logResponseMessage()");

            xRoadMessageLog.log(new SoapLogMessage(responseSoap, encoder.getSignature(), false, xRequestId));
        }
    }

    private void sendRequest(String serviceAddress, HttpSender httpSender) throws Exception {
        monitor.debug(() -> "sendRequest(%s)".formatted(serviceAddress));

        URI uri;
        try {
            uri = new URI(serviceAddress);
        } catch (URISyntaxException e) {
            throw new CodedException(X_SERVICE_MALFORMED_URL, "Malformed service address '%s': %s", serviceAddress,
                    e.getMessage());
        }

        monitor.debug(() -> "Sending request to %s".formatted(uri));
        try (InputStream in = requestMessage.getSoapContent()) {
            httpSender.doPost(uri, in, CHUNKED_LENGTH, jRequest.getHeaderString(HEADER_ORIGINAL_CONTENT_TYPE));
        } catch (Exception ex) {
            throw translateException(ex).withPrefix(X_SERVICE_FAILED_X);
        }
    }

    private void parseResponse(DefaultServiceHandlerImpl handler) throws Exception {
        monitor.debug("parseResponse()");

        preprocess();

        // Preserve the original content type of the service response
        responseBuilder.header(HEADER_ORIGINAL_CONTENT_TYPE, handler.getResponseContentType());

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

    }

    private void sign() throws Exception {
        monitor.debug(() -> "sign(%s)".formatted(requestServiceId.getClientId()));

        encoder.sign(responseSigningCtx);
    }

    private void writeSignature() throws Exception {
        monitor.debug("writeSignature()");

        encoder.writeSignature();
    }

    private void close() throws Exception {
        monitor.debug("close()");

        encoder.close();

        //todo: should start streaming earlier
        jResponse.resume(responseBuilder
                .entity((StreamingOutput) out -> cachingStream.getCachedContents().transferTo(out))
                .build());
    }

    private void handleException(Exception ex) throws Exception {
        if (encoder != null) {
            CodedException exception;

            if (ex instanceof CodedException.Fault) {
                exception = (CodedException.Fault) ex;
            } else {
                exception = translateWithPrefix(SERVER_SERVERPROXY_X, ex);
            }

            encoder.fault(SoapFault.createFaultXml(exception));
            encoder.close();
        } else {
            throw ex;
        }
    }

//    private X509Certificate getClientAuthCert() {
//        return clientSslCerts != null ? clientSslCerts[0] : null;
//    }

    private final class DefaultServiceHandlerImpl {

        private HttpSender sender;

        public boolean shouldVerifyAccess() {
            return true;
        }

        public boolean shouldVerifySignature() {
            return true;
        }

        public boolean shouldLogSignature() {
            return true;
        }

        public void startHandling(ContainerRequestContext request, ProxyMessage proxyRequestMessage) throws Exception {
            sender = createHttpSender();

            monitor.debug("processRequest(%s)".formatted(requestServiceId));

            String address = ServerConf.getServiceAddress(requestServiceId);

            if (address == null || address.isEmpty()) {
                throw new CodedException(X_SERVICE_MISSING_URL, "Service address not specified for '%s'",
                        requestServiceId);
            }

            int timeout = TimeUtils.secondsToMillis(ServerConf.getServiceTimeout(requestServiceId));

            sender.setConnectionTimeout(timeout);
            sender.setSocketTimeout(timeout);
            sender.setAttribute(ServiceId.class.getName(), requestServiceId);

            sender.addHeader("accept-encoding", "");
            sender.addHeader("SOAPAction", originalSoapAction);
            sendRequest(address, sender);
        }

        public void finishHandling() throws Exception {
            sender.close();
            sender = null;
        }

        public String getResponseContentType() {
            return sender.getResponseContentType();
        }

        public InputStream getResponseContent() {
            return sender.getResponseContent();
        }
    }

    private final class SoapMessageHandler implements SoapMessageDecoder.Callback {
        @Override
        public void soap(SoapMessage message, Map<String, String> headers) throws Exception {
            responseSoap = (SoapMessageImpl) message;

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
