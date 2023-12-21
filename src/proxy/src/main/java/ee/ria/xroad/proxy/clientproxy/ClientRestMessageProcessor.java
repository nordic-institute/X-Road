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
package ee.ria.xroad.proxy.clientproxy;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.cert.CertChain;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.IsAuthenticationData;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.message.RestResponse;
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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.Header;
import org.apache.http.client.HttpClient;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.message.BasicHeader;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.util.io.TeeInputStream;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.ErrorCodes.X_INCONSISTENT_RESPONSE;
import static ee.ria.xroad.common.ErrorCodes.X_IO_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_MISSING_REST;
import static ee.ria.xroad.common.ErrorCodes.X_MISSING_SIGNATURE;
import static ee.ria.xroad.common.ErrorCodes.X_SERVICE_FAILED_X;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_MESSAGE_TYPE;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_ORIGINAL_CONTENT_TYPE;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_REQUEST_ID;
import static ee.ria.xroad.common.util.MimeUtils.VALUE_MESSAGE_TYPE_REST;
import static ee.ria.xroad.common.util.MimeUtils.getBoundary;
import static ee.ria.xroad.common.util.TimeUtils.getEpochMillisecond;

@Slf4j
class ClientRestMessageProcessor extends AbstractClientMessageProcessor {

    private ServiceId requestServiceId;
    /**
     * Holds the response from server proxy.
     */
    private ProxyMessage response;

    private ClientId senderId;
    private RestRequest restRequest;
    private String xRequestId;
    private byte[] restBodyDigest;

    ClientRestMessageProcessor(HttpServletRequest servletRequest, HttpServletResponse servletResponse,
            HttpClient httpClient, IsAuthenticationData clientCert, OpMonitoringData opMonitoringData)
            throws Exception {
        super(servletRequest, servletResponse, httpClient, clientCert, opMonitoringData);
        this.xRequestId = UUID.randomUUID().toString();
    }

    @Override
    public void process() throws Exception {
        opMonitoringData.setXRequestId(xRequestId);
        updateOpMonitoringClientSecurityServerAddress();

        try {
            restRequest = new RestRequest(
                    servletRequest.getMethod(),
                    servletRequest.getRequestURI(),
                    servletRequest.getQueryString(),
                    headers(servletRequest),
                    xRequestId
            );

            // Check that incoming identifiers do not contain illegal characters
            checkRequestIdentifiers();

            senderId = restRequest.getClientId();
            requestServiceId = restRequest.getServiceId();

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

    private void checkRequestIdentifiers() {
        checkIdentifier(restRequest.getClientId());
        checkIdentifier(restRequest.getServiceId());
        checkIdentifier(restRequest.getTargetSecurityServer());
    }

    private void updateOpMonitoringClientSecurityServerAddress() {
        try {
            opMonitoringData.setClientSecurityServerAddress(getSecurityServerAddress());
        } catch (Exception e) {
            log.error("Failed to assign operational monitoring data field {}",
                    OpMonitoringData.CLIENT_SECURITY_SERVER_ADDRESS, e);
        }
    }

    private void updateOpMonitoringDataByResponse(ProxyMessageDecoder decoder) {
        if (response.getRestResponse() != null) {
            opMonitoringData.setResponseAttachmentCount(0);
            opMonitoringData.setResponseSize(response.getRestResponse().getMessageBytes().length
                    + decoder.getAttachmentsByteCount());
        }
    }

    private void processRequest() throws Exception {
        if (restRequest.getQueryId() == null) {
            restRequest.setQueryId(GlobalConf.getInstanceIdentifier() + "-" + UUID.randomUUID().toString());
        }
        updateOpMonitoringDataByRestRequest(opMonitoringData, restRequest);
        try (HttpSender httpSender = createHttpSender()) {
            sendRequest(httpSender);
            parseResponse(httpSender);
            checkConsistency(getHashAlgoId(httpSender));
        }
        logResponseMessage();
    }

    private void sendRequest(HttpSender httpSender) throws Exception {
        log.trace("sendRequest()");

        final URI[] addresses = prepareRequest(httpSender, requestServiceId, restRequest.getTargetSecurityServer());
        httpSender.addHeader(HEADER_MESSAGE_TYPE, VALUE_MESSAGE_TYPE_REST);

        // Add unique id to distinguish request/response pairs
        httpSender.addHeader(HEADER_REQUEST_ID, xRequestId);

        final String contentType = MimeUtils.mpMixedContentType("xtop" + RandomStringUtils.randomAlphabetic(30));
        opMonitoringData.setRequestOutTs(getEpochMillisecond());
        httpSender.doPost(getServiceAddress(addresses), new ProxyMessageEntity(contentType));
        opMonitoringData.setResponseInTs(getEpochMillisecond());
    }

    private void parseResponse(HttpSender httpSender) throws Exception {
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
        opMonitoringData.setRestResponseStatusCode(response.getRestResponse().getResponseCode());
        decoder.verify(requestServiceId.getClientId(), response.getSignature());
    }

    @Override
    public boolean verifyMessageExchangeSucceeded() {
        return response != null
                && response.getRestResponse() != null
                && !response.getRestResponse().isErrorResponse();
    }

    private void checkResponse() {
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

    private void checkConsistency(String hashAlgoId) throws IOException, OperatorCreationException {
        if (!Objects.equals(restRequest.getClientId(), response.getRestResponse().getClientId())) {
            throw new CodedException(X_INCONSISTENT_RESPONSE, "Response client id does not match request message");
        }
        if (!Objects.equals(restRequest.getQueryId(), response.getRestResponse().getQueryId())) {
            throw new CodedException(X_INCONSISTENT_RESPONSE, "Response message id does not match request message");
        }
        if (!Objects.equals(restRequest.getServiceId(), response.getRestResponse().getServiceId())) {
            throw new CodedException(X_INCONSISTENT_RESPONSE, "Response service id does not match request message");
        }
        if (!Objects.equals(restRequest.getXRequestId(), response.getRestResponse().getXRequestId())) {
            throw new CodedException(X_INCONSISTENT_RESPONSE,
                    "Response message request id does not match request message");
        }

        //calculate request hash
        byte[] requestDigest;
        if (restBodyDigest != null) {
            final DigestCalculator dc = CryptoUtils.createDigestCalculator(hashAlgoId);
            try (OutputStream out = dc.getOutputStream()) {
                out.write(restRequest.getHash());
                out.write(restBodyDigest);
            }
            requestDigest = dc.getDigest();
        } else {
            requestDigest = restRequest.getHash();
        }

        if (!Arrays.equals(requestDigest, response.getRestResponse().getRequestHash())) {
            throw new CodedException(X_INCONSISTENT_RESPONSE, "Response message hash does not match request message");
        }
    }

    private void logResponseMessage() {
        MessageLog.log(restRequest,
                response.getRestResponse(),
                response.getSignature(),
                response.getRestBody(), true, xRequestId);
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

                final CertChain chain = KeyConf.getAuthKey().getCertChain();
                KeyConf.getAllOcspResponses(chain.getAllCertsWithoutTrustedRoot())
                        .forEach(resp -> enc.ocspResponse(resp));

                enc.restRequest(restRequest);

                //Optimize the case without request body (e.g. simple get requests)
                //TBD: Optimize the case without body logging
                try (InputStream in = servletRequest.getInputStream()) {
                    @SuppressWarnings("checkstyle:magicnumber")
                    byte[] buf = new byte[4096];
                    int count = in.read(buf);
                    if (count >= 0) {
                        final CachingStream cache = new CachingStream();
                        try (TeeInputStream tee = new TeeInputStream(in, cache)) {
                            cache.write(buf, 0, count);
                            enc.restBody(buf, count, tee);
                            enc.sign(KeyConf.getSigningCtx(senderId));
                            MessageLog.log(restRequest, enc.getSignature(), cache.getCachedContents(), true,
                                    xRequestId);
                        } finally {
                            cache.consume();
                        }
                    } else {
                        enc.sign(KeyConf.getSigningCtx(senderId));
                        MessageLog.log(restRequest, enc.getSignature(), null, true, xRequestId);
                    }
                }

                opMonitoringData.setRequestAttachmentCount(0);
                opMonitoringData.setRequestSize(restRequest.getMessageBytes().length
                        + enc.getAttachmentsByteCount());

                restBodyDigest = enc.getRestBodyDigest();
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
        //Use jetty request to keep the original order
        Request jrq = (Request) req;
        return jrq.getHttpFields().stream()
                .map(f -> new BasicHeader(f.getName(), f.getValue()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

}
