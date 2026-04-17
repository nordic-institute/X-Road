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
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_SECURITY_SERVER;
import static org.niis.xroad.common.core.exception.ErrorCode.UNKNOWN_MEMBER;

/**
 * Resolves the candidate security servers that host a given service, honoring an optional
 * caller-sent {@link SecurityServerId} hint.
 *
 * <p>Shared between the traditional proxy routing path
 * ({@link DefaultServiceAddressResolver}) and the DSP acquire path
 * ({@code DspSubProcessor}, plan 09-04) so that hint semantics and error codes
 * ({@link org.niis.xroad.common.core.exception.ErrorCode#UNKNOWN_MEMBER} /
 * {@link org.niis.xroad.common.core.exception.ErrorCode#INVALID_SECURITY_SERVER})
 * stay in one place.
 *
 * <p>Maintenance-mode filtering is intentionally NOT performed here — it remains a
 * {@link DefaultServiceAddressResolver}-only concern per research D-15 /
 * 09-RESEARCH.md §"Open Questions (RESOLVED)" Q2.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class ProviderSecurityServerResolver {

    private final GlobalConfProvider globalConfProvider;

    /**
     * Resolves candidate (SecurityServerId, hostAddress) pairs that host {@code serviceId}.
     *
     * @param serviceId the target service (its {@link ServiceId#getClientId()} identifies
     *                  the provider subsystem/member)
     * @param hint      optional caller-sent security-server hint; when non-null, the result
     *                  is filtered to this exact SS
     * @return non-empty list of candidate (SecurityServerId, host-address) pairs;
     *         when {@code hint == null}, each entry has {@code serverId == null} and only
     *         {@code hostAddress} populated
     * @throws XrdRuntimeException {@code UNKNOWN_MEMBER}          — no provider addresses
     * @throws XrdRuntimeException {@code INVALID_SECURITY_SERVER} — hint absent from provider set
     */
    public List<ProviderAddress> resolve(ServiceId serviceId, @Nullable SecurityServerId hint) {
        log.trace("resolve({}, {})", serviceId, hint);

        var hostNames = hostNamesByProvider(serviceId);

        if (hint != null) {
            return List.of(new ProviderAddress(hint, hostNameBySecurityServer(hint, hostNames)));
        }

        var result = new ArrayList<ProviderAddress>(hostNames.size());
        for (var host : hostNames) {
            result.add(new ProviderAddress(null, host));
        }
        return result;
    }

    private Collection<String> hostNamesByProvider(ServiceId serviceProvider) {
        var hostNames = globalConfProvider.getProviderAddress(serviceProvider.getClientId());
        if (hostNames == null || hostNames.isEmpty()) {
            throw XrdRuntimeException.systemException(UNKNOWN_MEMBER,
                    "Could not find addresses for service provider \"%s\"".formatted(serviceProvider));
        }
        return hostNames;
    }

    private String hostNameBySecurityServer(SecurityServerId serverId, Collection<String> hostNamesByProvider) {
        final String securityServerAddress = globalConfProvider.getSecurityServerAddress(serverId);

        if (securityServerAddress == null) {
            throw XrdRuntimeException.systemException(INVALID_SECURITY_SERVER,
                    "Could not find security server \"%s\"".formatted(serverId));
        }

        if (!hostNamesByProvider.contains(securityServerAddress)) {
            throw XrdRuntimeException.systemException(INVALID_SECURITY_SERVER,
                    "Invalid security server \"%s\"".formatted(serverId));
        }

        return securityServerAddress;
    }

    /**
     * A single candidate endpoint for provider security-server selection.
     *
     * @param serverId    the security-server identifier when filtered via a caller hint;
     *                    {@code null} when the caller did not send a hint and the resolver
     *                    returned the full provider-address set
     * @param hostAddress the security-server host address (never null)
     */
    public record ProviderAddress(@Nullable SecurityServerId serverId, String hostAddress) {
    }
}
