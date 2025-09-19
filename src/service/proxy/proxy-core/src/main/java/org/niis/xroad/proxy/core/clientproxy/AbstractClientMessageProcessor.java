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
import org.niis.xroad.serverconf.model.Client;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static ee.ria.xroad.common.ErrorCodes.X_INVALID_CLIENT_IDENTIFIER;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_SECURITY_SERVER;
import static ee.ria.xroad.common.ErrorCodes.X_MAINTENANCE_MODE;
import static ee.ria.xroad.common.ErrorCodes.X_UNKNOWN_MEMBER;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_HASH_ALGO_ID;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_ORIGINAL_CONTENT_TYPE;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_PROXY_VERSION;
import static org.niis.xroad.proxy.core.clientproxy.FastestConnectionSelectingSSLSocketFactory.ID_TARGETS;

@Slf4j
abstract class AbstractClientMessageProcessor extends MessageProcessorBase {

    protected final IsAuthenticationData clientCert;
    protected final OpMonitoringData opMonitoringData;

    private final URI dummyServiceAddress;

    protected AbstractClientMessageProcessor(CommonBeanProxy commonBeanProxy,
                                             RequestWrapper request, ResponseWrapper response,
                                             HttpClient httpClient, IsAuthenticationData clientCert,
                                             OpMonitoringData opMonitoringData) {
        super(commonBeanProxy, request, response, httpClient);

        this.clientCert = clientCert;
        this.opMonitoringData = opMonitoringData;

        try {
            dummyServiceAddress = new URI("https", null, "localhost",
                   commonBeanProxy.getProxyProperties().serverProxyPort(), "/", null, null);
        } catch (URISyntaxException e) {
            //can not happen
            throw new IllegalStateException("Unexpected", e);
        }
    }

    protected URI getServiceAddress(URI[] addresses) {
        if (addresses.length == 1 || !commonBeanProxy.getProxyProperties().sslEnabled()) {
            return addresses[0];
        }
        //postpone actual name resolution to the fastest connection selector
        return dummyServiceAddress;
    }

    URI[] prepareRequest(HttpSender httpSender, ServiceId requestServiceId, SecurityServerId securityServerId) {
        // If we're using SSL, we need to include the provider name in
        // the HTTP request so that server proxy could verify the SSL
        // certificate properly.
        if (commonBeanProxy.getProxyProperties().sslEnabled()) {
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

        var hostNames = hostNamesByProvider(serviceProvider);

        if (serverId != null) {
            hostNames = hostNamesBySecurityServer(serverId, hostNames);
        }

        String protocol = commonBeanProxy.getProxyProperties().sslEnabled() ? "https" : "http";
        int port = commonBeanProxy.getProxyProperties().serverProxyPort();

        List<URI> addresses = new ArrayList<>(hostNames.size());

        var maintenanceModeErrors = new LinkedList<CodedException>();

        for (var host : hostNames) {
            var inMaintenance = commonBeanProxy.getGlobalConfProvider().getMaintenanceMode(serviceProvider.getXRoadInstance(), host)
                    .filter(SharedParameters.MaintenanceMode::enabled)
                    .map(mode -> buildMaintenanceModeException(null, host, mode.message()))
                    .map(maintenanceModeErrors::add)
                    .orElse(Boolean.FALSE);
            if (!inMaintenance) {
                buildUri(protocol, host, port).ifPresent(addresses::add);
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

    private Optional<URI> buildUri(String protocol, String host, int port) {
        try {
            return Optional.of(new URI(protocol, null, host, port, "/", null, null));
        } catch (URISyntaxException e) {
            log.warn("Invalid service provider hostname: {}", host);
            return Optional.empty();
        }
    }

    private Collection<String> hostNamesByProvider(ServiceId serviceProvider) {
        var hostNames = commonBeanProxy.getGlobalConfProvider().getProviderAddress(serviceProvider.getClientId());

        if (hostNames == null || hostNames.isEmpty()) {
            throw new CodedException(X_UNKNOWN_MEMBER, "Could not find addresses for service provider \"%s\"",
                    serviceProvider);
        }

        return hostNames;
    }

    private Collection<String> hostNamesBySecurityServer(SecurityServerId serverId, Collection<String> hostNamesByProvider) {
        final String securityServerAddress = commonBeanProxy.getGlobalConfProvider().getSecurityServerAddress(serverId);

        if (securityServerAddress == null) {
            throw new CodedException(X_INVALID_SECURITY_SERVER, "Could not find security server \"%s\"", serverId);
        }

        if (!hostNamesByProvider.contains(securityServerAddress)) {
            throw new CodedException(X_INVALID_SECURITY_SERVER, "Invalid security server \"%s\"", serverId);
        }

        commonBeanProxy.getGlobalConfProvider().getMaintenanceMode(serverId)
                .filter(SharedParameters.MaintenanceMode::enabled)
                .ifPresent(maintenanceMode -> {
                    throw buildMaintenanceModeException(serverId, securityServerAddress, maintenanceMode.message());
                });

        return Collections.singleton(securityServerAddress);
    }

    private CodedException buildMaintenanceModeException(SecurityServerId serverId, String address, String maintenanceModeMessage) {
        var serverIdStr = serverId != null ? serverId.toString() : null;
        var message = new StringBuilder("Security server");
        if (serverId != null) {
            message
                    .append(" \"")
                    .append(serverIdStr)
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
                    .append(". Message from \"")
                    .append(StringUtils.defaultIfEmpty(serverIdStr, address))
                    .append("\" administrator: ")
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

    protected void verifyClientAuthentication(ClientId sender) {
        if (!commonBeanProxy.getProxyProperties().verifyClientCert()) {
            return;
        }
        log.trace("verifyClientAuthentication()");
        verifyClientAuthentication(sender, clientCert);
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
