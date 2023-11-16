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
package ee.ria.xroad.proxy.serverproxy;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.cert.CertChain;
import ee.ria.xroad.common.cert.CertHelper;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.DescriptionType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.message.RestResponse;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapUtils;
import ee.ria.xroad.common.opmonitoring.OpMonitoringData;
import ee.ria.xroad.common.util.CachingStream;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.TimeUtils;
import ee.ria.xroad.proxy.conf.KeyConf;
import ee.ria.xroad.proxy.conf.SigningCtx;
import ee.ria.xroad.proxy.messagelog.MessageLog;
import ee.ria.xroad.proxy.protocol.ProxyMessage;
import ee.ria.xroad.proxy.protocol.ProxyMessageDecoder;
import ee.ria.xroad.proxy.protocol.ProxyMessageEncoder;
import ee.ria.xroad.proxy.util.MessageProcessorBase;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.TeeInputStream;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpTrace;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.bouncycastle.operator.DigestCalculator;

import java.io.OutputStream;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static ee.ria.xroad.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.X_ACCESS_DENIED;
import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_REQUEST;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_SERVICE_TYPE;
import static ee.ria.xroad.common.ErrorCodes.X_MISSING_REST;
import static ee.ria.xroad.common.ErrorCodes.X_MISSING_SIGNATURE;
import static ee.ria.xroad.common.ErrorCodes.X_SERVICE_DISABLED;
import static ee.ria.xroad.common.ErrorCodes.X_SERVICE_FAILED_X;
import static ee.ria.xroad.common.ErrorCodes.X_SERVICE_MISSING_URL;
import static ee.ria.xroad.common.ErrorCodes.X_SSL_AUTH_FAILED;
import static ee.ria.xroad.common.ErrorCodes.X_UNKNOWN_MEMBER;
import static ee.ria.xroad.common.ErrorCodes.X_UNKNOWN_SERVICE;
import static ee.ria.xroad.common.ErrorCodes.translateWithPrefix;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_HASH_ALGO_ID;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_ORIGINAL_CONTENT_TYPE;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_REQUEST_ID;
import static ee.ria.xroad.common.util.TimeUtils.getEpochMillisecond;

@Slf4j
class ServerRestMessageProcessor extends MessageProcessorBase {

    private static final String SERVERPROXY_REST_SERVICE_HANDLERS = SystemProperties.PREFIX
            + "proxy.serverRestServiceHandlers";

    private final X509Certificate[] clientSslCerts;

    private final List<RestServiceHandler> handlers = new ArrayList<>();

    private ProxyMessage requestMessage;
    private ServiceId requestServiceId;

    private ProxyMessageDecoder decoder;
    private ProxyMessageEncoder encoder;

    private SigningCtx responseSigningCtx;

    private OpMonitoringData opMonitoringData;
    private RestResponse restResponse;
    private CachingStream restResponseBody;

    private String xRequestId;

    ServerRestMessageProcessor(HttpServletRequest servletRequest,
                               HttpServletResponse servletResponse,
                               HttpClient httpClient,
                               X509Certificate[] clientSslCerts,
                               OpMonitoringData opMonitoringData) {
        super(servletRequest, servletResponse, httpClient);

        this.clientSslCerts = clientSslCerts;
        this.opMonitoringData = opMonitoringData;
        loadServiceHandlers();
    }

    @Override
    public void process() throws Exception {
        log.info("process({})", servletRequest.getContentType());

        xRequestId = servletRequest.getHeader(HEADER_REQUEST_ID);

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
            if (restResponseBody != null) {
                restResponseBody.consume();
            }
        }
    }

    private void updateOpMonitoringClientSecurityServerAddress() {
        try {
            X509Certificate authCert = getClientAuthCert();

            if (authCert != null) {
                opMonitoringData.setClientSecurityServerAddress(GlobalConf.getSecurityServerAddress(
                        GlobalConf.getServerId(authCert)));
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
    public boolean verifyMessageExchangeSucceeded() {
        return restResponse != null && !restResponse.isErrorResponse();
    }

    @Override
    protected void preprocess() throws Exception {
        encoder = new ProxyMessageEncoder(servletResponse.getOutputStream(), CryptoUtils.DEFAULT_DIGEST_ALGORITHM_ID);
        servletResponse.setContentType(encoder.getContentType());
        servletResponse.addHeader(HEADER_HASH_ALGO_ID, SoapUtils.getHashAlgoId());
    }

    @Override
    protected void postprocess() {
        opMonitoringData.setSucceeded(verifyMessageExchangeSucceeded());
        opMonitoringData.setRestResponseStatusCode(restResponse.getResponseCode());
    }

    private void loadServiceHandlers() {
        String serviceHandlerNames = System.getProperty(SERVERPROXY_REST_SERVICE_HANDLERS);
        if (!StringUtils.isBlank(serviceHandlerNames)) {
            for (String serviceHandlerName : serviceHandlerNames.split(",")) {
                handlers.add(RestServiceHandlerLoader.load(serviceHandlerName));
                log.trace("Loaded rest service handler: " + serviceHandlerName);
            }
        }
    }

    private RestServiceHandler getServiceHandler(ProxyMessage request) {
        for (RestServiceHandler handler : handlers) {
            if (handler.canHandle(requestServiceId, request)) {
                return handler;
            }
        }
        return null;
    }

    private void handleRequest() throws Exception {
        RestServiceHandler handler = getServiceHandler(requestMessage);
        if (handler == null) {
            handler = new DefaultRestServiceHandlerImpl();
        }
        log.trace("handler={}", handler);
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
            preprocess();
            handler.startHandling(servletRequest, requestMessage, decoder, encoder,
                    httpClient, null, opMonitoringData);
        } finally {
            handler.finishHandling();
            restResponse = handler.getRestResponse();
            restResponseBody = handler.getRestResponseBody();
        }
    }

    private void readMessage() throws Exception {
        log.trace("readMessage()");

        requestMessage = new ProxyMessage(servletRequest.getHeader(HEADER_ORIGINAL_CONTENT_TYPE)) {
            @Override
            public void rest(RestRequest message) throws Exception {
                super.rest(message);
                requestServiceId = message.getServiceId();
                verifyClientStatus();
                responseSigningCtx = KeyConf.getSigningCtx(requestServiceId.getClientId());
                if (SystemProperties.isSslEnabled()) {
                    verifySslClientCert();
                }
            }
        };

        decoder = new ProxyMessageDecoder(requestMessage, servletRequest.getContentType(), false,
                getHashAlgoId(servletRequest));
        try {
            decoder.parse(servletRequest.getInputStream());
        } catch (CodedException e) {
            throw e.withPrefix(X_SERVICE_FAILED_X);
        }

        updateOpMonitoringDataByRequest();

        // Check if the input contained all the required bits.
        checkRequest();
    }

    private void updateOpMonitoringDataByRequest() {
        updateOpMonitoringDataByRestRequest(opMonitoringData, requestMessage.getRest());
        opMonitoringData.setRequestAttachmentCount(0);
        opMonitoringData.setRequestSize(requestMessage.getRest().getMessageBytes().length
                + decoder.getAttachmentsByteCount());
    }

    private void checkRequest() {
        final RestRequest rest = requestMessage.getRest();
        if (rest == null) {
            throw new CodedException(X_MISSING_REST, "Request does not have REST message");
        }
        if (requestMessage.getSignature() == null) {
            throw new CodedException(X_MISSING_SIGNATURE, "Request does not have signature");
        }

        checkIdentifier(rest.getClientId());
        checkIdentifier(rest.getServiceId());
        checkIdentifier(rest.getTargetSecurityServer());
    }

    private void verifyClientStatus() {
        ClientId client = requestServiceId.getClientId();

        String status = ServerConf.getMemberStatus(client);

        if (!ClientType.STATUS_REGISTERED.equals(status)) {
            throw new CodedException(X_UNKNOWN_MEMBER, "Client '%s' not found", client);
        }
    }

    private void verifySslClientCert() throws Exception {
        if (requestMessage.getOcspResponses().isEmpty()) {
            throw new CodedException(X_SSL_AUTH_FAILED,
                    "Cannot verify TLS certificate, corresponding OCSP response is missing");
        }

        String instanceIdentifier = requestMessage.getRest().getClientId().getXRoadInstance();
        X509Certificate trustAnchor = GlobalConf.getCaCert(instanceIdentifier,
                clientSslCerts[clientSslCerts.length - 1]);

        if (trustAnchor == null) {
            throw new Exception("Unable to find trust anchor");
        }

        try {
            CertChain chain = CertChain.create(instanceIdentifier, (X509Certificate[]) ArrayUtils.add(clientSslCerts,
                    trustAnchor));
            CertHelper.verifyAuthCert(chain, requestMessage.getOcspResponses(), requestMessage.getRest().getClientId());
        } catch (Exception e) {
            throw new CodedException(X_SSL_AUTH_FAILED, e);
        }
    }

    private void verifyAccess() {
        log.trace("verifyAccess()");

        if (!ServerConf.serviceExists(requestServiceId)) {
            throw new CodedException(X_UNKNOWN_SERVICE, "Unknown service: %s", requestServiceId);
        }

        DescriptionType descriptionType = ServerConf.getDescriptionType(requestServiceId);
        if (descriptionType != null && descriptionType != DescriptionType.REST
                    && descriptionType != DescriptionType.OPENAPI3) {
            throw new CodedException(X_INVALID_SERVICE_TYPE,
                    "Service is a SOAP service and cannot be called using REST interface");
        }

        if (!ServerConf.isQueryAllowed(
                requestMessage.getRest().getClientId(),
                requestServiceId,
                requestMessage.getRest().getVerb().name(),
                requestMessage.getRest().getServicePath())) {
            throw new CodedException(X_ACCESS_DENIED, "Request is not allowed: %s", requestServiceId);
        }

        String disabledNotice = ServerConf.getDisabledNotice(requestServiceId);

        if (disabledNotice != null) {
            throw new CodedException(X_SERVICE_DISABLED, "Service %s is disabled: %s", requestServiceId,
                    disabledNotice);
        }
    }

    private void verifySignature() throws Exception {
        log.trace("verifySignature()");

        decoder.verify(requestMessage.getRest().getClientId(), requestMessage.getSignature());
    }

    private void logRequestMessage() {
        log.trace("logRequestMessage()");
        MessageLog.log(requestMessage.getRest(), requestMessage.getSignature(), requestMessage.getRestBody(),
                false, xRequestId);
    }

    private void logResponseMessage() {
        log.trace("log response message");
        MessageLog.log(requestMessage.getRest(), restResponse, encoder.getSignature(),
                restResponseBody == null ? null : restResponseBody.getCachedContents(), false, xRequestId);
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

        log.debug("Request failed", ex);

        if (encoder != null) {
            CodedException exception;
            if (ex instanceof CodedException.Fault) {
                exception = (CodedException.Fault) ex;
            } else {
                exception = translateWithPrefix(SERVER_SERVERPROXY_X, ex);
            }
            opMonitoringData.setFaultCodeAndString(exception);
            encoder.fault(SoapFault.createFaultXml(exception));
            encoder.close();
        } else {
            throw ex;
        }
    }

    private X509Certificate getClientAuthCert() {
        return clientSslCerts != null ? clientSslCerts[0] : null;
    }

    private static String getHashAlgoId(HttpServletRequest servletRequest) {
        String hashAlgoId = servletRequest.getHeader(HEADER_HASH_ALGO_ID);

        if (hashAlgoId == null) {
            throw new CodedException(X_INTERNAL_ERROR, "Could not get hash algorithm identifier from message");
        }

        return hashAlgoId;
    }

    private static class DefaultRestServiceHandlerImpl implements RestServiceHandler {

        private RestResponse restResponse;
        private CachingStream restResponseBody;

        private String concatPath(String address, String path) {
            if (path == null || path.isEmpty()) return address;
            if (address.endsWith("/") && path.startsWith("/")) {
                return address.concat(path.substring(1));
            }
            return address.concat(path);
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
        public void startHandling(HttpServletRequest servletRequest, ProxyMessage requestProxyMessage,
                                  ProxyMessageDecoder messageDecoder, ProxyMessageEncoder messageEncoder,
                                  HttpClient restClient, HttpClient opMonitorClient,
                                  OpMonitoringData monitoringData) throws Exception {
            String address = ServerConf.getServiceAddress(requestProxyMessage.getRest().getServiceId());
            if (address == null || address.isEmpty()) {
                throw new CodedException(X_SERVICE_MISSING_URL, "Service address not specified for '%s'",
                        requestProxyMessage.getRest().getServiceId());
            }

            address = concatPath(address, requestProxyMessage.getRest().getServicePath());
            final String query = requestProxyMessage.getRest().getQuery();
            if (query != null) {
                address += "?" + query;
            }

            HttpRequestBase req = switch (requestProxyMessage.getRest().getVerb()) {
                case GET -> new HttpGet(address);
                case POST -> new HttpPost(address);
                case PUT -> new HttpPut(address);
                case DELETE -> new HttpDelete(address);
                case PATCH -> new HttpPatch(address);
                case OPTIONS -> new HttpOptions(address);
                case HEAD -> new HttpHead(address);
                case TRACE -> new HttpTrace(address);
                default -> throw new CodedException(X_INVALID_REQUEST, "Unsupported REST verb");
            };

            int timeout = TimeUtils.secondsToMillis(ServerConf
                    .getServiceTimeout(requestProxyMessage.getRest().getServiceId()));
            req.setConfig(RequestConfig
                    .custom()
                    .setSocketTimeout(timeout)
                    .build());

            for (Header header : requestProxyMessage.getRest().getHeaders()) {
                req.addHeader(header);
            }

            if (req instanceof HttpEntityEnclosingRequest && requestProxyMessage.hasRestBody()) {
                ((HttpEntityEnclosingRequest) req).setEntity(new InputStreamEntity(requestProxyMessage.getRestBody(),
                        requestProxyMessage.getRestBody().size()));
            }

            final HttpContext ctx = new BasicHttpContext();
            ctx.setAttribute(ServiceId.class.getName(), requestProxyMessage.getRest().getServiceId());
            monitoringData.setRequestOutTs(getEpochMillisecond());
            final HttpResponse response = restClient.execute(req, ctx);
            monitoringData.setResponseInTs(getEpochMillisecond());
            final StatusLine statusLine = response.getStatusLine();

            //calculate request hash
            byte[] requestDigest;
            if (messageDecoder.getRestBodyDigest() != null) {
                final DigestCalculator dc = CryptoUtils.createDigestCalculator(CryptoUtils.DEFAULT_DIGEST_ALGORITHM_ID);
                try (OutputStream out = dc.getOutputStream()) {
                    out.write(requestProxyMessage.getRest().getHash());
                    out.write(messageDecoder.getRestBodyDigest());
                }
                requestDigest = dc.getDigest();
            } else {
                requestDigest = requestProxyMessage.getRest().getHash();
            }

            restResponse = new RestResponse(requestProxyMessage.getRest().getClientId(),
                    requestProxyMessage.getRest().getQueryId(),
                    requestDigest,
                    requestProxyMessage.getRest().getServiceId(),
                    statusLine.getStatusCode(),
                    statusLine.getReasonPhrase(),
                    Arrays.asList(response.getAllHeaders()),
                    servletRequest.getHeader(HEADER_REQUEST_ID)

            );
            messageEncoder.restResponse(restResponse);

            if (response.getEntity() != null) {
                restResponseBody = new CachingStream();
                TeeInputStream tee = new TeeInputStream(response.getEntity().getContent(), restResponseBody);
                messageEncoder.restBody(tee);
                EntityUtils.consume(response.getEntity());
            }

            monitoringData.setResponseAttachmentCount(0);
            monitoringData.setResponseSize(restResponse.getMessageBytes().length
                    + messageEncoder.getAttachmentsByteCount());
        }

        @Override
        public RestResponse getRestResponse() {
            return restResponse;
        }

        @Override
        public CachingStream getRestResponseBody() {
            return restResponseBody;
        }

        @Override
        public void finishHandling() throws Exception {
            // NOP
        }
    }
}
