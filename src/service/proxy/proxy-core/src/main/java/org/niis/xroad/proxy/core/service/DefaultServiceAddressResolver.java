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

import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.SharedParameters;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.service.ProviderSecurityServerResolver.ProviderAddress;
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

    /**
     * Resolves the provider's candidate servers, leaving out those in maintenance mode.
     *
     * @param serviceProvider the target service
     * @param serverId        the target security server, or null if not specified
     * @return candidates not in maintenance mode
     * @throws XrdRuntimeException {@code MAINTENANCE_MODE} when the targeted server, or every server, is in maintenance mode;
     *                             {@code UNKNOWN_MEMBER} or {@code INVALID_SECURITY_SERVER} when the provider or the
     *                             targeted server cannot be resolved
     */
    public List<ProviderAddress> resolveCandidates(ServiceId serviceProvider, @Nullable SecurityServerId serverId) {
        var filter = filterMaintenance(serviceProvider, serverId);

        if (filter.available().isEmpty()) {
            filter.firstMaintenanceError().ifPresent(error -> {
                throw error;
            });
        }

        return filter.available();
    }

    private List<URI> resolveFromGlobalConf(ServiceId serviceProvider, SecurityServerId serverId) {
        log.trace("resolveFromGlobalConf({}, {})", serviceProvider, serverId);

        var filter = filterMaintenance(serviceProvider, serverId);

        String protocol = proxyProperties.sslEnabled() ? "https" : "http";
        int port = proxyProperties.serverProxyPort();

        List<URI> addresses = new ArrayList<>(filter.available().size());
        for (var candidate : filter.available()) {
            buildUri(protocol, candidate.hostAddress(), port).ifPresent(addresses::add);
        }

        if (addresses.isEmpty()) {
            throw filter.firstMaintenanceError().orElseGet(() -> XrdRuntimeException.systemException(UNKNOWN_MEMBER,
                    "Could not find suitable address for service provider \"%s\"".formatted(serviceProvider)));
        }

        return addresses;
    }

    private MaintenanceFilter filterMaintenance(ServiceId serviceProvider, @Nullable SecurityServerId serverId) {
        var candidates = providerSecurityServerResolver.resolve(serviceProvider, serverId);

        // ProviderSecurityServerResolver is intentionally maintenance-unaware, so the hinted server
        // is checked here by its id and every candidate by its host address below.
        if (serverId != null) {
            var hintHost = candidates.get(0).hostAddress();
            globalConfProvider.getMaintenanceMode(serverId)
                    .filter(SharedParameters.MaintenanceMode::enabled)
                    .ifPresent(maintenanceMode -> {
                        throw buildMaintenanceModeException(serverId, hintHost, maintenanceMode.message());
                    });
        }

        var available = new ArrayList<ProviderAddress>(candidates.size());
        var maintenanceModeErrors = new LinkedList<XrdRuntimeException>();

        for (var candidate : candidates) {
            var host = candidate.hostAddress();
            var inMaintenance = globalConfProvider.getMaintenanceMode(serviceProvider.getXRoadInstance(), host)
                    .filter(SharedParameters.MaintenanceMode::enabled)
                    .map(mode -> buildMaintenanceModeException(null, host, mode.message()))
                    .map(maintenanceModeErrors::add)
                    .orElse(Boolean.FALSE);
            if (!inMaintenance) {
                available.add(candidate);
            }
        }

        return new MaintenanceFilter(available, maintenanceModeErrors.stream().findFirst());
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

    private record MaintenanceFilter(List<ProviderAddress> available,
                                     Optional<XrdRuntimeException> firstMaintenanceError) {
    }
}
