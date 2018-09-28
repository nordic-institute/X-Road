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
package ee.ria.xroad.proxy.clientproxy;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.cert.CertChain;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.IsAuthentication;
import ee.ria.xroad.common.conf.serverconf.IsAuthenticationData;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.message.SoapUtils;
import ee.ria.xroad.common.monitoring.MessageInfo;
import ee.ria.xroad.common.monitoring.MonitorAgent;
import ee.ria.xroad.common.opmonitoring.OpMonitoringData;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.HttpSender;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.proxy.ProxyMain;
import ee.ria.xroad.proxy.conf.KeyConf;
import ee.ria.xroad.proxy.protocol.ProxyMessage;
import ee.ria.xroad.proxy.protocol.ProxyMessageDecoder;
import ee.ria.xroad.proxy.protocol.ProxyMessageEncoder;
import ee.ria.xroad.proxy.util.MessageProcessorBase;

import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.http.Header;
import org.apache.http.client.HttpClient;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.message.BasicHeader;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.eclipse.jetty.server.Response;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static ee.ria.xroad.common.ErrorCodes.X_INCONSISTENT_RESPONSE;
import static ee.ria.xroad.common.ErrorCodes.X_IO_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_MISSING_SIGNATURE;
import static ee.ria.xroad.common.ErrorCodes.X_MISSING_SOAP;
import static ee.ria.xroad.common.ErrorCodes.X_SERVICE_FAILED_X;
import static ee.ria.xroad.common.ErrorCodes.X_UNKNOWN_MEMBER;
import static ee.ria.xroad.common.SystemProperties.getServerProxyPort;
import static ee.ria.xroad.common.SystemProperties.isSslEnabled;
import static ee.ria.xroad.common.util.CryptoUtils.decodeBase64;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_HASH_ALGO_ID;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_ORIGINAL_CONTENT_TYPE;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_PROXY_VERSION;
import static ee.ria.xroad.common.util.MimeUtils.getBoundary;
import static ee.ria.xroad.proxy.clientproxy.FastestConnectionSelectingSSLSocketFactory.ID_TARGETS;

@Slf4j
class ClientRestMessageProcessor extends MessageProcessorBase {

    /**
     * Holds the client side SSL certificate.
     */
    private final IsAuthenticationData clientCert;

    private ServiceId requestServiceId;

    /**
     * If the request failed, will contain SOAP fault.
     */
    private CodedException executionException;

    /**
     * Holds the proxy message output stream and associated info.
     */
    private PipedInputStream reqIns;
    private volatile PipedOutputStream reqOuts;
    private volatile String outputContentType;

    /**
     * Holds the request to the server proxy.
     */
    private ProxyMessageEncoder request;

    /**
     * Holds the response from server proxy.
     */
    private ProxyMessage response;

    //** Holds operational monitoring data. */
    private volatile OpMonitoringData opMonitoringData;
    private ClientId senderId;
    private RestRequest restRequest;

    ClientRestMessageProcessor(HttpServletRequest servletRequest, HttpServletResponse servletResponse,
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

        try {
            restRequest = new RestRequest(
                    servletRequest.getMethod(),
                    join("?", servletRequest.getRequestURI(), servletRequest.getQueryString()),
                    headers(servletRequest)
            );

            senderId = restRequest.getClient();
            requestServiceId = restRequest.getRequestServiceId();

            verifyClientStatus();
            verifyClientAuthentication();

            processRequest();
            if (response != null) {
                sendResponse();
            }
        } catch (Exception e) {
            if (reqIns != null) {
                reqIns.close();
            }
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
        if (response.getRestResponse() != null) {
            opMonitoringData.setResponseSoapSize(0);
            opMonitoringData.setResponseAttachmentCount(0);
        }
    }


    private void processRequest() throws Exception {
        log.trace("processRequest()");

        try (HttpSender httpSender = createHttpSender()) {
            sendRequest(httpSender);
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
            List<URI> tmp = getServiceAddresses(requestServiceId);
            Collections.shuffle(tmp);
            URI[] addresses = tmp.toArray(new URI[0]);

            updateOpMonitoringServiceSecurityServerAddress(addresses, httpSender);

            httpSender.setAttribute(ID_TARGETS, addresses);

            if (SystemProperties.isEnableClientProxyPooledConnectionReuse()) {
                httpSender.setAttribute(HttpClientContext.USER_TOKEN, new TargetHostsUserToken(addresses));
            }

            httpSender.setConnectionTimeout(SystemProperties.getClientProxyTimeout());
            httpSender.setSocketTimeout(SystemProperties.getClientProxyHttpClientTimeout());

            httpSender.addHeader(HEADER_HASH_ALGO_ID, SoapUtils.getHashAlgoId());
            httpSender.addHeader(HEADER_PROXY_VERSION, ProxyMain.readProxyVersion());
            httpSender.addHeader(HEADER_ORIGINAL_CONTENT_TYPE, servletRequest.getContentType());
            httpSender.addHeader("X-Road-Message-Type", "REST");

            try {
                final String contentType = MimeUtils.mpMixedContentType("xtop"
                        + RandomStringUtils.randomAlphabetic(30));
                httpSender.doPost(addresses[0], new ProxyMessageEntity(contentType));
            } catch (Exception e) {
                MonitorAgent.serverProxyFailed(createRequestMessageInfo());
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

    private void checkResponse() {
        log.trace("checkResponse()");

        if (response.getFault() != null) {
            throw response.getFault().toCodedException();
        }

        if (response.getRestResponse() == null) {
            throw new CodedException(X_MISSING_SOAP, "Response does not have REST message");
        }

        if (response.getSignature() == null) {
            throw new CodedException(X_MISSING_SIGNATURE, "Response does not have signature");
        }
    }

    private void checkConsistency() throws Exception {
        checkRequestHash();
    }

    private void checkRequestHash() {
        final Header header = response.getRestResponse().getHeaders().stream()
                .filter(h -> "X-Road-Request-Hash".equalsIgnoreCase(h.getName()))
                .findAny()
                .orElseThrow(() -> new CodedException(X_INCONSISTENT_RESPONSE,
                        "Response from server proxy is missing request message hash"));

        if (!Arrays.equals(restRequest.getHash(), decodeBase64(header.getValue()))) {
            throw new CodedException(X_INCONSISTENT_RESPONSE,
                    "Request message hash does not match request message");
        }
    }

    private void logResponseMessage() throws Exception {
        log.trace("logResponseMessage()");
        //MessageLog.log(response.getSoap(), response.getSignature(), true);
    }

    private void sendResponse() throws Exception {
        if (servletResponse instanceof Response) {
            // the standard API for setting reason and code is deprecated
            ((Response) servletResponse).setStatusWithReason(
                    response.getRestResponse().getResponseCode(),
                    response.getRestResponse().getReason());
        } else {
            servletResponse.setStatus(response.getRestResponse().getResponseCode());
        }
        for (Header h : response.getRestResponse().getHeaders()) {
            servletResponse.addHeader(h.getName(), h.getValue());
        }
        if (response.getRestBody() != null) {
            IOUtils.copy(response.getRestBody(), servletResponse.getOutputStream());
        }
    }

    @Override
    public MessageInfo createRequestMessageInfo() {
        if (restRequest == null) {
            return null;
        }

        return new MessageInfo(MessageInfo.Origin.CLIENT_PROXY,
                restRequest.getClient(),
                requestServiceId,
                null,
                null);
    }

    protected void verifyClientStatus() {

        String status = ServerConf.getMemberStatus(senderId);
        if (!ClientType.STATUS_REGISTERED.equals(status)) {
            throw new CodedException(X_UNKNOWN_MEMBER, "Client '%s' not found", senderId);
        }
    }

    protected void verifyClientAuthentication() throws Exception {
        if (!SystemProperties.shouldVerifyClientCert()) {
            return;
        }
        IsAuthentication.verifyClientAuthentication(senderId, clientCert);
    }

    private static List<URI> getServiceAddresses(ServiceId serviceProvider)
            throws Exception {
        Collection<String> hostNames = GlobalConf.getProviderAddress(serviceProvider.getClientId());
        if (hostNames == null || hostNames.isEmpty()) {
            throw new CodedException(X_UNKNOWN_MEMBER, "Could not find addresses for service provider \"%s\"",
                    serviceProvider);
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


    class ProxyMessageEntity extends AbstractHttpEntity {

        ProxyMessageEntity(String contentType) {
            super();
            setContentType(contentType);
        }

        @Override
        public boolean isRepeatable() {
            return false;
        }

        @Override
        public long getContentLength() {
            return -1;
        }

        @Override
        public InputStream getContent() throws UnsupportedOperationException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeTo(OutputStream outstream) {
            try {
                final ProxyMessageEncoder enc = new ProxyMessageEncoder(outstream,
                        CryptoUtils.DEFAULT_DIGEST_ALGORITHM_ID, getBoundary(contentType.getValue()));

                CertChain chain = KeyConf.getAuthKey().getCertChain();
                List<OCSPResp> ocspResponses = KeyConf.getAllOcspResponses(chain.getAllCertsWithoutTrustedRoot());

                for (OCSPResp ocsp : ocspResponses) {
                    enc.ocspResponse(ocsp);
                }
                restRequest.getHeaders().add(new BasicHeader("X-Road-Id", "xrd-" + UUID.randomUUID().toString()));
                enc.restRequest(restRequest);
                enc.restBody(servletRequest.getInputStream());
                enc.sign(KeyConf.getSigningCtx(senderId));
                enc.writeSignature();
                enc.close();

            } catch (Exception e) {
                throw new CodedException(X_IO_ERROR, e);
            }

        }

        @Override
        public boolean isStreaming() {
            return true;
        }
    }

    private String join(String delim, String first, String second) {
        if (second == null || second.isEmpty()) return first;
        return first + delim + second;
    }

    private List<Header> headers(HttpServletRequest req) {
        final ArrayList<Header> tmp = new ArrayList<>();
        final Enumeration<String> names = req.getHeaderNames();
        while (names.hasMoreElements()) {
            final String header = names.nextElement();
            final Enumeration<String> headers = req.getHeaders(header);
            while (headers.hasMoreElements()) {
                final String value = headers.nextElement();
                tmp.add(new BasicHeader(header, value));
            }
        }
        return tmp;
    }

}
