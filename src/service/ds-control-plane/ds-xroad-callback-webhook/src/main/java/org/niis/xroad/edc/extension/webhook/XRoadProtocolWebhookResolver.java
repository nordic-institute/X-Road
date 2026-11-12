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
package org.niis.xroad.edc.extension.webhook;

import ee.ria.xroad.common.identifier.SecurityServerId;

import jakarta.annotation.Nullable;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.eclipse.edc.protocol.spi.DataspaceProfileContext;
import org.eclipse.edc.protocol.spi.DataspaceProfileContextRegistry;
import org.eclipse.edc.protocol.spi.ProtocolWebhook;
import org.eclipse.edc.protocol.spi.ProtocolWebhookResolver;
import org.eclipse.edc.spi.monitor.Monitor;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.ds.identity.DspConventions;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.GlobalConfInitException;
import org.niis.xroad.globalconf.model.ServerSystemValues;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.util.Optional;

import static org.niis.xroad.common.core.exception.ErrorCode.MALFORMED_SERVERCONF;

/**
 * Resolves the DSP callback of this Security Server from the DSP base URL that globalconf publishes for it. Globalconf
 * is read on every call, so a newly published or changed value applies without a restart. While the value is not
 * available no webhook is returned, which makes EDC end the affected negotiation or transfer instead of failing.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class XRoadProtocolWebhookResolver implements ProtocolWebhookResolver {

    private final GlobalConfProvider globalConfProvider;
    private final ServerConfProvider serverConfProvider;
    private final DataspaceProfileContextRegistry profileRegistry;
    private final Monitor monitor;

    @Override
    public @Nullable ProtocolWebhook getWebhook(String participantContextId, String protocol) {
        if (!isKnownProfile(protocol)) {
            return null;
        }
        return publishedDspBaseUrl()
                .map(base -> base + "/" + DspConventions.participantPathSegment(participantContextId) + "/" + protocol)
                .<ProtocolWebhook>map(url -> () -> url)
                .orElse(null);
    }

    private boolean isKnownProfile(String protocol) {
        return profileRegistry.getProfiles().stream().map(DataspaceProfileContext::name).anyMatch(name -> name.equals(protocol));
    }

    private Optional<String> publishedDspBaseUrl() {
        try {
            return ownIdentifier().flatMap(this::publishedDspBaseUrl);
        } catch (GlobalConfInitException _) {
            monitor.warning("DSP callback unavailable: global configuration is not initialised, so the published DSP base URL "
                    + "cannot be looked up");
            return Optional.empty();
        }
    }

    private Optional<SecurityServerId> ownIdentifier() {
        try {
            return Optional.of(serverConfProvider.getIdentifier());
        } catch (XrdRuntimeException e) {
            if (!e.isCausedBy(MALFORMED_SERVERCONF)) {
                throw e;
            }
            monitor.warning("DSP callback unavailable: the Security Server owner is not set, so its published DSP base URL "
                    + "cannot be looked up");
            return Optional.empty();
        }
    }

    private Optional<String> publishedDspBaseUrl(SecurityServerId serverId) {
        var published = globalConfProvider.getServerSystemValues(serverId).map(ServerSystemValues::dspBaseUrl);
        if (published.isEmpty()) {
            monitor.warning("DSP callback unavailable: global configuration publishes no DSP base URL for " + serverId.toShortString());
        }
        return published;
    }
}
