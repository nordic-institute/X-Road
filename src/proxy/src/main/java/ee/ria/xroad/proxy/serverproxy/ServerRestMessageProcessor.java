/**
 * The MIT License
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
import ee.ria.xroad.common.cert.CertHelper;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.message.RestResponse;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapUtils;
import ee.ria.xroad.common.monitoring.MessageInfo;
import ee.ria.xroad.common.monitoring.MessageInfo.Origin;
import ee.ria.xroad.common.monitoring.MonitorAgent;
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

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.TeeInputStream;
import org.apache.commons.lang.ArrayUtils;
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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static ee.ria.xroad.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.X_ACCESS_DENIED;
import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_REQUEST;
import static ee.ria.xroad.common.ErrorCodes.X_MISSING_SIGNATURE;
import static ee.ria.xroad.common.ErrorCodes.X_MISSING_SOAP;
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

@Slf4j
class ServerRestMessageProcessor extends MessageProcessorBase {

    private final X509Certificate[] clientSslCerts;

    private final List<ServiceHandler> handlers = new ArrayList<>();

    private ProxyMessage requestMessage;
    private ServiceId requestServiceId;

    private ProxyMessageDecoder decoder;
    private ProxyMessageEncoder encoder;

    private SigningCtx responseSigningCtx;

    private HttpClient opMonitorHttpClient;
    private OpMonitoringData opMonitoringData;
    private RestResponse restResponse;
    private CachingStream restResponseBody;

    private String xRequestId;

    ServerRestMessageProcessor(HttpServletRequest servletRequest, HttpServletResponse servletResponse,
            HttpClient httpClient, X509Certificate[] clientSslCerts, HttpClient opMonitorHttpClient,
            OpMonitoringData opMonitoringData) {
        super(servletRequest, servletResponse, httpClient);

        this.clientSslCerts = clientSslCerts;
        this.opMonitorHttpClient = opMonitorHttpClient;
        this.opMonitoringData = opMonitoringData;
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
            verifyAccess();
            verifySignature();
            logRequestMessage();
            sendRequest();
            sign();
            logResponseMessage();
            writeSignature();
            close();
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
    protected void preprocess() throws Exception {
        encoder = new ProxyMessageEncoder(servletResponse.getOutputStream(), CryptoUtils.DEFAULT_DIGEST_ALGORITHM_ID);
        servletResponse.setContentType(encoder.getContentType());
        servletResponse.addHeader(HEADER_HASH_ALGO_ID, SoapUtils.getHashAlgoId());
    }

    @Override
    protected void postprocess() throws Exception {
        opMonitoringData.setSucceeded(true);
    }

    private void readMessage() throws Exception {
        log.trace("readMessage()");

        requestMessage = new ProxyMessage(servletRequest.getHeader(HEADER_ORIGINAL_CONTENT_TYPE)) {
            @Override
            public void rest(RestRequest message) throws Exception {
                super.rest(message);
                requestServiceId = message.getRequestServiceId();
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
        if (requestMessage.getSoap() != null) {
            opMonitoringData.setRequestAttachmentCount(decoder.getAttachmentCount());

            if (decoder.getAttachmentCount() > 0) {
                opMonitoringData.setRequestMimeSize(requestMessage.getSoap().getBytes().length
                        + decoder.getAttachmentsByteCount());
            }
        }
    }

    private void checkRequest() throws Exception {
        if (requestMessage.getRest() == null) {
            throw new CodedException(X_MISSING_SOAP, "Request does not have REST message");
        }

        if (requestMessage.getSignature() == null) {
            throw new CodedException(X_MISSING_SIGNATURE, "Request does not have signature");
        }
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

        String instanceIdentifier = requestMessage.getRest().getClient().getXRoadInstance();
        X509Certificate trustAnchor = GlobalConf.getCaCert(instanceIdentifier,
                clientSslCerts[clientSslCerts.length - 1]);

        if (trustAnchor == null) {
            throw new Exception("Unable to find trust anchor");
        }

        try {
            CertChain chain = CertChain.create(instanceIdentifier, (X509Certificate[]) ArrayUtils.add(clientSslCerts,
                    trustAnchor));
            CertHelper.verifyAuthCert(chain, requestMessage.getOcspResponses(), requestMessage.getRest().getClient());
        } catch (Exception e) {
            throw new CodedException(X_SSL_AUTH_FAILED, e);
        }
    }

    private void verifyAccess() {
        log.trace("verifyAccess()");

        if (!ServerConf.serviceExists(requestServiceId)) {
            throw new CodedException(X_UNKNOWN_SERVICE, "Unknown service: %s", requestServiceId);
        }

        if (!ServerConf.isQueryAllowed(requestMessage.getRest().getClient(), requestServiceId)) {
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

        decoder.verify(requestMessage.getRest().getClient(), requestMessage.getSignature());
    }

    private void sendRequest() throws Exception {

        log.trace("processRequest({})", requestServiceId);
        String address = ServerConf.getServiceAddress(requestServiceId);
        if (address == null || address.isEmpty()) {
            throw new CodedException(X_SERVICE_MISSING_URL, "Service address not specified for '%s'",
                    requestServiceId);
        }

        address += requestMessage.getRest().getServicePath();
        final String query = requestMessage.getRest().getQuery();
        if (query != null) {
            address += "?" + query;
        }

        HttpRequestBase req;
        switch (requestMessage.getRest().getVerb()) {
            case GET:
                req = new HttpGet(address);
                break;
            case POST:
                req = new HttpPost(address);
                break;
            case PUT:
                req = new HttpPut(address);
                break;
            case DELETE:
                req = new HttpDelete(address);
                break;
            case PATCH:
                req = new HttpPatch(address);
                break;
            case OPTIONS:
                req = new HttpOptions(address);
                break;
            case HEAD:
                req = new HttpHead(address);
                break;
            case TRACE:
                req = new HttpTrace(address);
                break;
            default:
                throw new CodedException(X_INVALID_REQUEST, "Unsupported REST verb");
        }

        int timeout = TimeUtils.secondsToMillis(ServerConf.getServiceTimeout(requestServiceId));
        req.setConfig(RequestConfig
                .custom()
                .setSocketTimeout(timeout)
                .build());

        for (Header header : requestMessage.getRest().getHeaders()) {
            req.addHeader(header);
        }

        if (req instanceof HttpEntityEnclosingRequest && requestMessage.hasRestBody()) {
            ((HttpEntityEnclosingRequest) req).setEntity(new InputStreamEntity(requestMessage.getRestBody()));
        }

        preprocess();

        final HttpContext ctx = new BasicHttpContext();
        ctx.setAttribute(ServiceId.class.getName(), requestServiceId);
        final HttpResponse response = httpClient.execute(req, ctx);
        final StatusLine statusLine = response.getStatusLine();
        restResponse = new RestResponse(requestMessage.getRest().getQueryId(),
                requestMessage.getRest().getHash(),
                requestServiceId,
                statusLine.getStatusCode(),
                statusLine.getReasonPhrase(),
                Arrays.asList(response.getAllHeaders()));
        encoder.restResponse(restResponse);

        if (response.getEntity() != null) {
            restResponseBody = new CachingStream();
            TeeInputStream tee = new TeeInputStream(response.getEntity().getContent(), restResponseBody);
            encoder.restBody(tee);
            EntityUtils.consume(response.getEntity());
        }
    }

    private void logRequestMessage() {
        log.trace("logRequestMessage()");
        MessageLog.log(requestMessage.getRest(), requestMessage.getSignature(), requestMessage.getRestBody(),
                false, xRequestId);
    }

    private void logResponseMessage() {
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

            opMonitoringData.setSoapFault(exception);

            monitorAgentNotifyFailure(exception);

            encoder.fault(SoapFault.createFaultXml(exception));
            encoder.close();
        } else {
            throw ex;
        }
    }

    private void monitorAgentNotifyFailure(CodedException ex) {
        MessageInfo info = null;

        boolean requestIsComplete = requestMessage != null && requestMessage.getSoap() != null
                && requestMessage.getSignature() != null;

        // Include the request message only if the error was caused while
        // exchanging information with the adapter server.
        if (requestIsComplete && ex.getFaultCode().startsWith(SERVER_SERVERPROXY_X + "." + X_SERVICE_FAILED_X)) {
            info = createRequestMessageInfo();
        }

        MonitorAgent.failure(info, ex.getFaultCode(), ex.getFaultString());
    }

    @Override
    public MessageInfo createRequestMessageInfo() {
        if (requestMessage == null) {
            return null;
        }
        final RestRequest rest = requestMessage.getRest();
        return new MessageInfo(Origin.SERVER_PROXY, rest.getClient(), requestServiceId, null, null);
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

}
