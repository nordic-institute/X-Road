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
package org.niis.xroad.proxy.core.service;

import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.SharedParameters;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.util.ProxyRequestContext;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static org.niis.xroad.common.core.exception.ErrorCode.MAINTENANCE_MODE;
import static org.niis.xroad.common.core.exception.ErrorCode.UNKNOWN_MEMBER;

/**
 * Default address resolver using GlobalConf with maintenance mode filtering.
 *
 * <p>Resolves addresses via GlobalConf, filters out addresses in maintenance mode, and throws on empty results.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class DefaultServiceAddressResolver implements ServiceAddressResolver {

    private final GlobalConfProvider globalConfProvider;
    private final ProxyProperties proxyProperties;
    private final ProviderSecurityServerResolver providerSecurityServerResolver;

    @Override
    public List<URI> resolve(ServiceId serviceProvider, SecurityServerId securityServerId, ProxyRequestContext ctx) {
        return resolveFromGlobalConf(serviceProvider, securityServerId);
    }

    private List<URI> resolveFromGlobalConf(ServiceId serviceProvider, SecurityServerId serverId) {
        log.trace("resolveFromGlobalConf({}, {})", serviceProvider, serverId);

        // Delegate candidate SS selection to the shared helper (D-15 / D-16).
        var candidates = providerSecurityServerResolver.resolve(serviceProvider, serverId);

        // Preserve hint-path maintenance-mode semantics — the helper is maintenance-unaware
        // per 09-RESEARCH.md §"Open Questions (RESOLVED)" Q2. For the hint-absent path,
        // maintenance-mode is still applied by the per-host loop below (unchanged).
        if (serverId != null) {
            var hintHost = candidates.get(0).hostAddress();
            globalConfProvider.getMaintenanceMode(serverId)
                    .filter(SharedParameters.MaintenanceMode::enabled)
                    .ifPresent(maintenanceMode -> {
                        throw buildMaintenanceModeException(serverId, hintHost, maintenanceMode.message());
                    });
        }

        var hostNames = candidates.stream()
                .map(ProviderSecurityServerResolver.ProviderAddress::hostAddress)
                .toList();

        String protocol = proxyProperties.sslEnabled() ? "https" : "http";
        int port = proxyProperties.serverProxyPort();

        List<URI> addresses = new ArrayList<>(hostNames.size());
        var maintenanceModeErrors = new LinkedList<XrdRuntimeException>();

        for (var host : hostNames) {
            var inMaintenance = globalConfProvider.getMaintenanceMode(serviceProvider.getXRoadInstance(), host)
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
                throw XrdRuntimeException.systemException(UNKNOWN_MEMBER,
                        "Could not find suitable address for service provider \"%s\"".formatted(serviceProvider));
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

    private XrdRuntimeException buildMaintenanceModeException(SecurityServerId serverId, String address,
                                                               String maintenanceModeMessage) {
        var serverIdStr = serverId != null ? serverId.toString() : null;
        var message = new StringBuilder("Security server");
        if (serverId != null) {
            message.append(" \"")
                    .append(serverIdStr)
                    .append("\"");
        }

        if (StringUtils.isNotEmpty(address)) {
            message.append(" with address \"")
                    .append(address)
                    .append("\"");
        }

        message.append(" is in maintenance mode");

        if (StringUtils.isNotEmpty(maintenanceModeMessage)) {
            message.append(". Message from \"")
                    .append(StringUtils.defaultIfEmpty(serverIdStr, address))
                    .append("\" administrator: ")
                    .append(maintenanceModeMessage);
        }

        return XrdRuntimeException.systemException(MAINTENANCE_MODE, message.toString());
    }
}
