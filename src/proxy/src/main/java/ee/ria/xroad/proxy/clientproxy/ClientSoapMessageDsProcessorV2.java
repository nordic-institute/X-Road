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
package ee.ria.xroad.proxy.clientproxy;

import ee.ria.xroad.common.conf.serverconf.IsAuthenticationData;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.opmonitoring.OpMonitoringData;
import ee.ria.xroad.common.util.HttpSender;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpClient;
import org.niis.xroad.proxy.edc.AssetAuthorizationManager;
import org.niis.xroad.proxy.edc.AuthorizedAssetRegistry;
import org.niis.xroad.proxy.edc.TargetSecurityServerLookup;

import java.net.URI;
import java.util.List;

@Slf4j
class ClientSoapMessageDsProcessorV2 extends ClientSoapMessageProcessor {

    private final AssetAuthorizationManager assetAuthorizationManager;
    private AuthorizedAssetRegistry.GrantedAssetInfo assetInfo;

    ClientSoapMessageDsProcessorV2(RequestWrapper request, ResponseWrapper response, HttpClient httpClient,
                                   IsAuthenticationData clientCert, OpMonitoringData opMonitoringData,
                                   AssetAuthorizationManager assetAuthorizationManager) throws Exception {
        super(request, response, httpClient, clientCert, opMonitoringData);
        this.assetAuthorizationManager = assetAuthorizationManager;
    }

    @Override
    List<URI> getServiceAddresses(ServiceId serviceProvider, SecurityServerId serverId) throws Exception {
        var targetServers = TargetSecurityServerLookup.resolveTargetSecurityServers(serviceProvider.getClientId());

        //TODO xroad8 in POC we're not selecting fastest server, neither handle failure with fallbacks
        var targetServerInfo = targetServers.servers().stream().findFirst().orElseThrow();
        assetInfo = assetAuthorizationManager.getOrRequestAssetAccess(requestSoap.getClient(), targetServerInfo,
                requestSoap.getService(), false);

        return List.of(URI.create(assetInfo.endpoint() + "/proxy"));
    }

    @Override
    URI[] prepareRequest(HttpSender httpSender, ServiceId requestServiceId, SecurityServerId securityServerId) throws Exception {
        var result = super.prepareRequest(httpSender, requestServiceId, securityServerId);
        httpSender.addHeader("Authorization", assetInfo.authCode());
        return result;
    }

}
