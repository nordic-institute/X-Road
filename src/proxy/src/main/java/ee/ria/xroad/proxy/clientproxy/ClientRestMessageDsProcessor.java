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

import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.IsAuthenticationData;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.message.RestResponse;
import ee.ria.xroad.common.opmonitoring.OpMonitoringData;
import ee.ria.xroad.common.util.CachingStream;
import ee.ria.xroad.common.util.ResponseWrapper;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.HttpClient;
import org.niis.xroad.edc.sig.XrdSignatureService;
import org.niis.xroad.proxy.clientproxy.validate.RequestValidator;
import org.niis.xroad.proxy.edc.AssetAuthorizationManager;
import org.niis.xroad.proxy.edc.AuthorizedAssetRegistry;
import org.niis.xroad.proxy.edc.XrdDataSpaceClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static ee.ria.xroad.common.util.TimeUtils.getEpochMillisecond;

@Slf4j
class ClientRestMessageDsProcessor extends AbstractClientMessageProcessor {
    private final RequestValidator requestValidator = new RequestValidator();

    private final RestRequest restRequest;

    private final AssetAuthorizationManager assetAuthorizationManager;
    private final AbstractClientProxyHandler.ProxyRequestCtx proxyRequestCtx;

    private final XrdDataSpaceClient xrdDataSpaceClient = new XrdDataSpaceClient();
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
        opMonitoringData.setXRequestId(restRequest.getXRequestId());
        updateOpMonitoringClientSecurityServerAddress();

        try {
            ClientId senderId = restRequest.getClientId();

            checkRequestIdentifiers();
            requestValidator.verifyClientStatus(senderId);
            requestValidator.verifyClientAuthentication(senderId, clientCert);

            //TODO xroad8 in POC we're not selecting fastest server, neither handle failure with fallbacks
            var targetServerInfo = proxyRequestCtx.targetSecurityServers().servers().stream().findFirst().orElseThrow();

            var assetInfo = assetAuthorizationManager.getOrRequestAssetAccess(senderId, targetServerInfo, restRequest.getServiceId());

            processRequest(assetInfo);
        } finally {
            log.trace("DataSpace proxy request fully processed");
            opMonitoringData.setResponseInTs(getEpochMillisecond());
        }
    }

    private void processRequest(AuthorizedAssetRegistry.GrantedAssetInfo assetInfo) throws Exception {
        if (restRequest.getQueryId() == null) {
            restRequest.setQueryId(GlobalConf.getInstanceIdentifier() + "-" + UUID.randomUUID());
        }
//        //TODO op monitoring should know about DataSpace
        updateOpMonitoringDataByRestRequest(opMonitoringData, restRequest);

        CachingStream cachingStream = new CachingStream();
        jRequest.getInputStream().transferTo(cachingStream);
        restRequest.setBody(cachingStream);

        var response = xrdDataSpaceClient.processRestReqeust(restRequest, assetInfo);
        processResponse(response, jResponse);
    }

    private void processResponse(RestResponse response, ResponseWrapper jResponse) throws Exception {
        log.trace("sendResponse()");
        jResponse.setStatus(response.getResponseCode());

        Map<String, String> headers = new HashMap<>();
        response.getHeaders().forEach(h -> headers.put(h.getName(), h.getValue()));

        byte[] payload = response.getBody().getCachedContents().readAllBytes();

        //TODO handle bad request/edc failure
        // todo: use streams
        xrdSignatureService.verify(headers, payload, restRequest.getServiceId().getClientId());

        headers.forEach(jResponse.getHeaders()::add);

        try (InputStream body = new ByteArrayInputStream(payload)) {
            IOUtils.copy(body, jResponse.getOutputStream());
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

}
