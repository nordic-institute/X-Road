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

import ee.ria.xroad.common.cert.CertChainFactory;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.conf.serverconf.IsAuthenticationData;
import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.util.HttpSender;
import ee.ria.xroad.proxy.conf.KeyConfProvider;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpClient;
import org.niis.xroad.proxy.edc.AssetAuthorizationManager;
import org.niis.xroad.proxy.edc.AuthorizedAssetRegistry;

import java.net.URI;
import java.util.List;

@Slf4j
class ClientRestMessageDsProcessorV2 extends ClientRestMessageProcessor {

    private final AssetAuthorizationManager assetAuthorizationManager;
    private AuthorizedAssetRegistry.GrantedAssetInfo assetInfo;
    private final AbstractClientProxyHandler.ProxyRequestCtx proxyRequestCtx;

    ClientRestMessageDsProcessorV2(AbstractClientProxyHandler.ProxyRequestCtx proxyRequestCtx, RestRequest restRequest,
                                   GlobalConfProvider globalConfProvider, KeyConfProvider keyConfProvider,
                                   ServerConfProvider serverConfProvider, CertChainFactory certChainFactory,
                                   HttpClient httpClient, IsAuthenticationData clientCert,
                                   AssetAuthorizationManager assetAuthorizationManager)
            throws Exception {
        super(proxyRequestCtx, restRequest, globalConfProvider, keyConfProvider, serverConfProvider, certChainFactory, httpClient,
                clientCert);
        this.assetAuthorizationManager = assetAuthorizationManager;
        this.proxyRequestCtx = proxyRequestCtx;
    }

    @Override
    List<URI> getServiceAddresses(ServiceId serviceProvider, SecurityServerId serverId) throws Exception {
        //TODO xroad8 in POC we're not selecting fastest server, neither handle failure with fallbacks
        var targetServerInfo = proxyRequestCtx.targetSecurityServers().servers().stream().findFirst().orElseThrow();
        assetInfo = assetAuthorizationManager.getOrRequestAssetAccess(restRequest.getClientId(),
                targetServerInfo, restRequest.getServiceId(),
                proxyRequestCtx.alwaysReevaluatePolicies());

        return List.of(URI.create(assetInfo.endpoint() + "/proxy"));
    }

    @Override
    URI[] prepareRequest(HttpSender httpSender, ServiceId requestServiceId, SecurityServerId securityServerId) throws Exception {
        var result = super.prepareRequest(httpSender, requestServiceId, securityServerId);
        httpSender.addHeader("Authorization", assetInfo.authCode());
        return result;
    }

}
