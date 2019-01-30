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
import ee.ria.xroad.common.cert.CertChain;
import ee.ria.xroad.common.conf.serverconf.IsAuthenticationData;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.message.RestResponse;
import ee.ria.xroad.common.monitoring.MessageInfo;
import ee.ria.xroad.common.monitoring.MonitorAgent;
import ee.ria.xroad.common.opmonitoring.OpMonitoringData;
import ee.ria.xroad.common.util.CachingStream;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.HttpSender;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.proxy.conf.KeyConf;
import ee.ria.xroad.proxy.messagelog.MessageLog;
import ee.ria.xroad.proxy.protocol.ProxyMessage;
import ee.ria.xroad.proxy.protocol.ProxyMessageDecoder;
import ee.ria.xroad.proxy.protocol.ProxyMessageEncoder;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.http.Header;
import org.apache.http.client.HttpClient;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.message.BasicHeader;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.util.io.TeeInputStream;
import org.eclipse.jetty.server.Response;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static ee.ria.xroad.common.ErrorCodes.X_INCONSISTENT_RESPONSE;
import static ee.ria.xroad.common.ErrorCodes.X_IO_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_MISSING_REST;
import static ee.ria.xroad.common.ErrorCodes.X_MISSING_SIGNATURE;
import static ee.ria.xroad.common.ErrorCodes.X_SERVICE_FAILED_X;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_MESSAGE_TYPE;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_ORIGINAL_CONTENT_TYPE;
import static ee.ria.xroad.common.util.MimeUtils.VALUE_MESSAGE_TYPE_REST;
import static ee.ria.xroad.common.util.MimeUtils.getBoundary;

@Slf4j
class ClientRestMessageProcessor extends AbstractClientMessageProcessor {

    private ServiceId requestServiceId;
    /**
     * Holds the response from server proxy.
     */
    private ProxyMessage response;

    private ClientId senderId;
    private RestRequest restRequest;

    ClientRestMessageProcessor(HttpServletRequest servletRequest, HttpServletResponse servletResponse,
            HttpClient httpClient, IsAuthenticationData clientCert, OpMonitoringData opMonitoringData)
            throws Exception {
        super(servletRequest, servletResponse, httpClient, clientCert, opMonitoringData);
    }

    @Override
    public void process() throws Exception {
        log.trace("process()");
        updateOpMonitoringClientSecurityServerAddress();

        try {
            restRequest = new RestRequest(
                    servletRequest.getMethod(),
                    servletRequest.getRequestURI(),
                    servletRequest.getQueryString(),
                    headers(servletRequest)
            );

            senderId = restRequest.getClient();
            requestServiceId = restRequest.getRequestServiceId();

            verifyClientStatus(senderId);
            verifyClientAuthentication(senderId);

            processRequest();
            if (response != null) {
                sendResponse();
            }
        } catch (Exception e) {
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

        if (restRequest.getQueryId() == null) {
            restRequest.setQueryId("xrd-" + UUID.randomUUID().toString());
        }

        try (HttpSender httpSender = createHttpSender()) {
            sendRequest(httpSender);
            parseResponse(httpSender);
        }

        checkConsistency();
        logResponseMessage();
    }

    private void sendRequest(HttpSender httpSender) throws Exception {
        log.trace("sendRequest()");

        final URI[] addresses = prepareRequest(httpSender, requestServiceId, null);
        httpSender.addHeader(HEADER_MESSAGE_TYPE, VALUE_MESSAGE_TYPE_REST);

        try {
            final String contentType = MimeUtils.mpMixedContentType("xtop" + RandomStringUtils.randomAlphabetic(30));
            httpSender.doPost(addresses[0], new ProxyMessageEntity(contentType));
        } catch (Exception e) {
            MonitorAgent.serverProxyFailed(createRequestMessageInfo());
            throw e;
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
            throw new CodedException(X_MISSING_REST, "Response does not have REST message");
        }

        if (response.getSignature() == null) {
            throw new CodedException(X_MISSING_SIGNATURE, "Response does not have signature");
        }
    }

    private void checkConsistency() {
        if (!Objects.equals(restRequest.getQueryId(), response.getRestResponse().getQueryId())) {
            throw new CodedException(X_INCONSISTENT_RESPONSE, "Response message id does not match request message");
        }
        if (!Arrays.equals(restRequest.getHash(), response.getRestResponse().getRequestHash())) {
            throw new CodedException(X_INCONSISTENT_RESPONSE, "Response message hash does not match request message");
        }
    }

    private void logResponseMessage() {
        log.trace("logResponseMessage()");
        MessageLog.log(restRequest,
                response.getRestResponse(),
                response.getSignature(),
                response.getRestBody(), true);
    }

    private void sendResponse() throws Exception {
        final RestResponse rest = response.getRestResponse();

        if (servletResponse instanceof Response) {
            // the standard API for setting reason and code is deprecated
            ((Response) servletResponse).setStatusWithReason(
                    rest.getResponseCode(),
                    rest.getReason());
        } else {
            servletResponse.setStatus(rest.getResponseCode());
        }
        servletResponse.setHeader("Date", null);
        for (Header h : rest.getHeaders()) {
            servletResponse.addHeader(h.getName(), h.getValue());
        }
        if (response.hasRestBody()) {
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
        public void writeTo(OutputStream outstream) throws IOException {

            try {
                final ProxyMessageEncoder enc = new ProxyMessageEncoder(outstream,
                        CryptoUtils.DEFAULT_DIGEST_ALGORITHM_ID, getBoundary(contentType.getValue()));

                CertChain chain = KeyConf.getAuthKey().getCertChain();
                List<OCSPResp> ocspResponses = KeyConf.getAllOcspResponses(chain.getAllCertsWithoutTrustedRoot());

                for (OCSPResp ocsp : ocspResponses) {
                    enc.ocspResponse(ocsp);
                }

                enc.restRequest(restRequest);

                //Optimize the case without request body (e.g. simple get requests)
                try (PushbackInputStream in = new PushbackInputStream(servletRequest.getInputStream(), 1)) {
                    int b = in.read();
                    if (b >= 0) {
                        in.unread(b);
                        final CachingStream cache = new CachingStream();
                        try (TeeInputStream tee = new TeeInputStream(in, cache)) {
                            enc.restBody(tee);
                            enc.sign(KeyConf.getSigningCtx(senderId));
                            MessageLog.log(restRequest, enc.getSignature(), cache.getCachedContents(), true);
                        } finally {
                            cache.consume();
                        }
                    } else {
                        enc.sign(KeyConf.getSigningCtx(senderId));
                        MessageLog.log(restRequest, enc.getSignature(), null, true);
                    }
                }

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
