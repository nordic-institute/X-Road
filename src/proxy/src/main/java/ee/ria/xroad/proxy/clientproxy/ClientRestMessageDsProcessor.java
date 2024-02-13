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
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.IsAuthenticationData;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.opmonitoring.OpMonitoringData;
import ee.ria.xroad.common.util.HttpSender;
import ee.ria.xroad.common.util.MimeUtils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.entity.StringEntity;
import org.niis.xroad.edc.sig.XrdSignatureService;
import org.niis.xroad.proxy.edc.AssetAuthorizationManager;
import org.niis.xroad.proxy.edc.AuthorizedAssetRegistry;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static ee.ria.xroad.common.ErrorCodes.X_INVALID_REQUEST;
import static ee.ria.xroad.common.util.TimeUtils.getEpochMillisecond;

@Slf4j
class ClientRestMessageDsProcessor extends AbstractClientMessageProcessor {

    private final RestRequest restRequest;

    private final AssetAuthorizationManager assetAuthorizationManager;

    private final AbstractClientProxyHandler.ProxyRequestCtx proxyRequestCtx;
    private final XrdSignatureService xrdSignatureService = new XrdSignatureService();

    ClientRestMessageDsProcessor(final AbstractClientProxyHandler.ProxyRequestCtx proxyRequestCtx,
                                 final RestRequest restRequest,
                                 final HttpClient httpClient, final IsAuthenticationData clientCert,
                                 final AssetAuthorizationManager assetAuthorizationManager) {
        super(proxyRequestCtx, httpClient, clientCert);
        this.proxyRequestCtx = proxyRequestCtx;
        this.restRequest = restRequest;
        this.assetAuthorizationManager = assetAuthorizationManager;
    }

    //TODO rethink what should happen in constructor and what in process..
    @Override
    public void process() throws Exception {
//        opMonitoringData.setXRequestId(restRequest.getXRequestId());
        updateOpMonitoringClientSecurityServerAddress();

        try {
            ClientId senderId = restRequest.getClientId();

            checkRequestIdentifiers();
            verifyClientStatus(senderId);
            verifyClientAuthentication(senderId);

            //TODO xroad8 in POC we're not selecting fastest server, neither handle failure with fallbacks
            var targetServerInfo = proxyRequestCtx.targetSecurityServers().servers().stream().findFirst().orElseThrow();

            var assetInfo = assetAuthorizationManager.getOrRequestAssetAccess(senderId, targetServerInfo, restRequest.getServiceId());

            processRequest(assetInfo);

        } finally {
            log.trace("DataSpace proxy request fully processed");
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

    private void processRequest(AuthorizedAssetRegistry.GrantedAssetInfo assetInfo) throws Exception {
        if (restRequest.getQueryId() == null) {
            restRequest.setQueryId(GlobalConf.getInstanceIdentifier() + "-" + UUID.randomUUID());
        }
        //TODO op monitoring should know about DataSpace
        updateOpMonitoringDataByRestRequest(opMonitoringData, restRequest);

        try (HttpSender httpSender = createHttpSender()) {
            sendRequest(httpSender, assetInfo);

            processResponse(httpSender);
        }
    }

    private void processResponse(HttpSender httpSender) throws Exception {
        //TODO handle statuses
        servletResponse.setStatus(200);
        //TODO also headers..
        var outputStream = new ByteArrayOutputStream();
        IOUtils.copy(httpSender.getResponseContent(), outputStream);

        xrdSignatureService.verify(httpSender.getResponseHeaders(), outputStream.toByteArray(), restRequest.getClientId());
        outputStream.writeTo(servletResponse.getOutputStream());

        int headersSizeInBytes = 0;
        for (Map.Entry<String, String> entry : httpSender.getResponseHeaders().entrySet()) {
            // Each header has a name and value, and the ": " delimiter, and CRLF as overhead
            headersSizeInBytes += entry.getKey().length() + ": ".length() + entry.getValue().length() + "\r\n".length();
            servletResponse.addHeader(entry.getKey(), entry.getValue());
        }
        log.info("EDC public api response headers size: {} KB", headersSizeInBytes / 1024.0);
    }

    private void sendRequest(HttpSender httpSender, AuthorizedAssetRegistry.GrantedAssetInfo assetInfo) throws Exception {
        var rawJson = IOUtils.toString(servletRequest.getInputStream(), servletRequest.getCharacterEncoding());

        var headersToSign = getRequestHeaders();
        headersToSign.put(MimeUtils.HEADER_QUERY_ID, restRequest.getQueryId());
        headersToSign.put(assetInfo.authKey(), assetInfo.authCode());

        var headers = xrdSignatureService.sign(restRequest.getClientId(), rawJson, headersToSign);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            httpSender.addHeader(entry.getKey(), entry.getValue());
        }

        var path = assetInfo.endpoint();
        if (restRequest.getServicePath() != null) {
            path = path + restRequest.getServicePath();
        }
        if (StringUtils.isNotBlank(restRequest.getQuery())) {
            path += "?" + restRequest.getQuery();
        }

        var url = URI.create(path);

        log.info("Will send [{}] request to {}", restRequest.getVerb(), path);
        // todo: signature is required for messagelog

//        MessageLog.log(restRequest, new SignatureData(null, null, null), null, true, restRequest.getXRequestId());
        switch (restRequest.getVerb()) {
            case GET -> httpSender.doGet(url);
            case POST -> httpSender.doPost(url, new StringEntity(rawJson));
            default -> throw new CodedException(X_INVALID_REQUEST, "Unsupported verb");
        }

        opMonitoringData.setResponseInTs(getEpochMillisecond());
    }

    private Map<String, String> getRequestHeaders() {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = servletRequest.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.put(headerName, servletRequest.getHeader(headerName));
        }
        return headers;
    }
}
