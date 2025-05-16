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
package org.niis.xroad.proxy.core.clientproxy;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.Version;
import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.SoapUtils;
import ee.ria.xroad.common.util.HttpSender;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;

import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.client.protocol.HttpClientContext;
import org.niis.xroad.globalconf.model.SharedParameters;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.proxy.core.util.CommonBeanProxy;
import org.niis.xroad.proxy.core.util.MessageProcessorBase;
import org.niis.xroad.serverconf.impl.IsAuthenticationData;
import org.niis.xroad.serverconf.model.Client;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static ee.ria.xroad.common.ErrorCodes.X_INVALID_CLIENT_IDENTIFIER;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_SECURITY_SERVER;
import static ee.ria.xroad.common.ErrorCodes.X_MAINTENANCE_MODE;
import static ee.ria.xroad.common.ErrorCodes.X_UNKNOWN_MEMBER;
import static ee.ria.xroad.common.SystemProperties.getServerProxyPort;
import static ee.ria.xroad.common.SystemProperties.isSslEnabled;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_HASH_ALGO_ID;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_ORIGINAL_CONTENT_TYPE;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_PROXY_VERSION;
import static org.niis.xroad.proxy.core.clientproxy.FastestConnectionSelectingSSLSocketFactory.ID_TARGETS;

@Slf4j
abstract class AbstractClientMessageProcessor extends MessageProcessorBase {

    protected final IsAuthenticationData clientCert;
    protected final OpMonitoringData opMonitoringData;

    private static final URI DUMMY_SERVICE_ADDRESS;

    static {
        try {
            DUMMY_SERVICE_ADDRESS = new URI("https", null, "localhost", getServerProxyPort(), "/", null, null);
        } catch (URISyntaxException e) {
            //can not happen
            throw new IllegalStateException("Unexpected", e);
        }
    }

    protected AbstractClientMessageProcessor(CommonBeanProxy commonBeanProxy,
                                             RequestWrapper request, ResponseWrapper response,
                                             HttpClient httpClient, IsAuthenticationData clientCert,
                                             OpMonitoringData opMonitoringData) {
        super(commonBeanProxy, request, response, httpClient);

        this.clientCert = clientCert;
        this.opMonitoringData = opMonitoringData;
    }

    protected static URI getServiceAddress(URI[] addresses) {
        if (addresses.length == 1 || !isSslEnabled()) {
            return addresses[0];
        }
        //postpone actual name resolution to the fastest connection selector
        return DUMMY_SERVICE_ADDRESS;
    }

    URI[] prepareRequest(HttpSender httpSender, ServiceId requestServiceId, SecurityServerId securityServerId)
            throws Exception {
        // If we're using SSL, we need to include the provider name in
        // the HTTP request so that server proxy could verify the SSL
        // certificate properly.
        if (isSslEnabled()) {
            httpSender.setAttribute(AuthTrustVerifier.ID_PROVIDERNAME, requestServiceId);
        }

        // Start sending the request to server proxies. The underlying
        // SSLConnectionSocketFactory will select the fastest address
        // (socket that connects first) from the provided addresses.
        List<URI> tmp = getServiceAddresses(requestServiceId, securityServerId);
        Collections.shuffle(tmp);
        URI[] addresses = tmp.toArray(new URI[0]);

        updateOpMonitoringServiceSecurityServerAddress(addresses, httpSender);

        httpSender.setAttribute(ID_TARGETS, addresses);

        if (SystemProperties.isEnableClientProxyPooledConnectionReuse()) {
            // set the servers with this subsystem as the user token, this will pool the connections per groups of
            // security servers.
            httpSender.setAttribute(HttpClientContext.USER_TOKEN, new TargetHostsUserToken(addresses));
        }

        httpSender.setConnectionTimeout(SystemProperties.getClientProxyTimeout());
        httpSender.setSocketTimeout(SystemProperties.getClientProxyHttpClientTimeout());

        httpSender.addHeader(HEADER_HASH_ALGO_ID, SoapUtils.getHashAlgoId().name());
        httpSender.addHeader(HEADER_PROXY_VERSION, Version.XROAD_VERSION);

        // Preserve the original content type in the "x-original-content-type"
        // HTTP header, which will be used to send the request to the
        // service provider
        httpSender.addHeader(HEADER_ORIGINAL_CONTENT_TYPE, jRequest.getContentType());

        return addresses;
    }

    private void updateOpMonitoringServiceSecurityServerAddress(URI[] addresses, HttpSender httpSender) {
        if (addresses.length == 1) {
            opMonitoringData.setServiceSecurityServerAddress(addresses[0].getHost());
        } else {
            // In case multiple addresses the service security server
            // address will be founded by received TLS authentication
            // certificate in AuthTrustVerifier class.

            httpSender.setAttribute(OpMonitoringData.class.getName(), opMonitoringData);
        }
    }

    List<URI> getServiceAddresses(ServiceId serviceProvider, SecurityServerId serverId) {
        log.trace("getServiceAddresses({}, {})", serviceProvider, serverId);

        var hostNames = commonBeanProxy.getGlobalConfProvider().getProviderAddress(serviceProvider.getClientId());

        if (hostNames == null || hostNames.isEmpty()) {
            throw new CodedException(X_UNKNOWN_MEMBER, "Could not find addresses for service provider \"%s\"",
                    serviceProvider);
        }

        if (serverId != null) {
            final String securityServerAddress = commonBeanProxy.getGlobalConfProvider().getSecurityServerAddress(serverId);

            if (securityServerAddress == null) {
                throw new CodedException(X_INVALID_SECURITY_SERVER, "Could not find security server \"%s\"", serverId);
            }

            if (!hostNames.contains(securityServerAddress)) {
                throw new CodedException(X_INVALID_SECURITY_SERVER, "Invalid security server \"%s\"", serverId);
            }

            commonBeanProxy.globalConfProvider.getMaintenanceMode(serverId)
                    .filter(SharedParameters.MaintenanceMode::enabled)
                    .ifPresent(maintenanceMode -> {
                        throw buildMaintenanceModeException(serverId, securityServerAddress, maintenanceMode.message());
                    });

            hostNames = Collections.singleton(securityServerAddress);
        }

        String protocol = isSslEnabled() ? "https" : "http";
        int port = getServerProxyPort();

        List<URI> addresses = new ArrayList<>(hostNames.size());

        var maintenanceModeErrors = new LinkedList<CodedException>();

        for (var host : hostNames) {
            var addedError = commonBeanProxy.globalConfProvider.getMaintenanceMode(serviceProvider.getXRoadInstance(), host)
                    .filter(SharedParameters.MaintenanceMode::enabled)
                    .map(mode -> buildMaintenanceModeException(null, host, mode.message()))
                    .map(maintenanceModeErrors::add)
                    .orElse(Boolean.FALSE);
            if (addedError) {
                continue;
            }
            try {
                addresses.add(new URI(protocol, null, host, port, "/", null, null));
            } catch (URISyntaxException e) {
                log.warn("Invalid service provider hostname: {}", host);
            }
        }

        if (addresses.isEmpty()) {
            if (maintenanceModeErrors.isEmpty()) {
                throw new CodedException(X_UNKNOWN_MEMBER, "Could not find suitable address for service provider \"%s\"",
                        serviceProvider);
            } else {
                throw maintenanceModeErrors.getFirst();
            }
        }

        return addresses;
    }

    private CodedException buildMaintenanceModeException(SecurityServerId serverId, String address, String maintenanceModeMessage) {
        var message = new StringBuilder("Security server");
        if (serverId != null) {
            message
                    .append(" \"")
                    .append(serverId)
                    .append("\"");

        }

        if (StringUtils.isNotEmpty(address)) {
            message
                    .append(" with address \"")
                    .append(address)
                    .append("\"");
        }

        message.append(" is in maintenance mode");

        if (StringUtils.isNotEmpty(maintenanceModeMessage)) {
            message
                    .append(". ")
                    .append(maintenanceModeMessage);
        }

        return new CodedException(X_MAINTENANCE_MODE, message.toString());
    }

    static DigestAlgorithm getHashAlgoId(HttpSender httpSender) {
        return DigestAlgorithm.ofName(httpSender.getResponseHeaders().get(HEADER_HASH_ALGO_ID));
    }

    protected void verifyClientStatus(ClientId client) {
        if (client == null) {
            throw new CodedException(X_INVALID_CLIENT_IDENTIFIER, "The client identifier is missing");
        }

        String status = commonBeanProxy.getServerConfProvider().getMemberStatus(client);
        if (!Client.STATUS_REGISTERED.equals(status)) {
            throw new CodedException(X_UNKNOWN_MEMBER, "Client '%s' not found", client);
        }
    }

    protected void verifyClientAuthentication(ClientId sender) throws Exception {
        if (!SystemProperties.shouldVerifyClientCert()) {
            return;
        }
        log.trace("verifyClientAuthentication()");
        verifyClientAuthentication(sender, clientCert);
    }

    protected void verifyClientProxyStatus() {
        var mMode = commonBeanProxy.serverConfProvider.getMaintenanceMode();
        if (mMode.enabled()) {
            var message = "Server '%s' is in maintenance mode.".formatted(commonBeanProxy.serverConfProvider.getIdentifier());
            if (StringUtils.isNotEmpty(mMode.message())) {
                message += " " + mMode.message();
            }
            throw new CodedException(X_MAINTENANCE_MODE, message);
        }
    }

    @EqualsAndHashCode
    public static final class TargetHostsUserToken {
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
}
