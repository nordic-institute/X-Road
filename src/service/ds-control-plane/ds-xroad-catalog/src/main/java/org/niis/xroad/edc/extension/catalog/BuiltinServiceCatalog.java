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
package org.niis.xroad.edc.extension.catalog;

import ee.ria.xroad.common.identifier.ServiceId;

import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Single source of truth for built-in server-proxy service handler entries.
 *
 * <p>Built-in handlers are wired directly in {@code ServiceHandlerLoader} and never registered
 * in serverconf. This class emits synthetic catalog entries for them so the DSP catalog can
 * surface these services to consumers.
 *
 * <p>Each addon group can be disabled independently via
 * {@code xroad.dsp.builtin-services.{name}.enabled} config keys (default {@code true}).
 */
@Slf4j
class BuiltinServiceCatalog {

    static final String PROXY_MONITOR_SERVICE_CODE = "getSecurityServerMetrics";
    static final String OP_MONITOR_OPERATIONAL_DATA_SERVICE_CODE = "getSecurityServerOperationalData";
    static final String OP_MONITOR_HEALTH_DATA_SERVICE_CODE = "getSecurityServerHealthData";
    static final String META_LIST_METHODS_SERVICE_CODE = "listMethods";
    static final String META_ALLOWED_METHODS_SERVICE_CODE = "allowedMethods";
    static final String META_GET_WSDL_SERVICE_CODE = "getWsdl";
    static final String META_GET_OPEN_API_SERVICE_CODE = "getOpenAPI";

    static final String SETTING_PROXY_MONITOR_ENABLED = "xroad.dsp.builtin-services.proxyMonitor.enabled";
    static final String SETTING_OP_MONITOR_ENABLED = "xroad.dsp.builtin-services.opMonitor.enabled";
    static final String SETTING_METASERVICES_ENABLED = "xroad.dsp.builtin-services.metaservices.enabled";
    static final String SETTING_SERVER_PROXY_URL = "xroad.dsp.builtin-services.server-proxy-url";
    static final String DEFAULT_SERVER_PROXY_URL = "http://localhost:5500/";

    private final ServerConfProvider serverConfProvider;
    private final boolean proxyMonitorEnabled;
    private final boolean opMonitorEnabled;
    private final boolean metaservicesEnabled;
    private final String serverProxyUrl;
    private volatile Map<String, ServiceId.Conf> activeServiceIdsCache;

    BuiltinServiceCatalog(ServerConfProvider serverConfProvider,
                          boolean proxyMonitorEnabled,
                          boolean opMonitorEnabled,
                          boolean metaservicesEnabled,
                          String serverProxyUrl) {
        this.serverConfProvider = serverConfProvider;
        this.proxyMonitorEnabled = proxyMonitorEnabled;
        this.opMonitorEnabled = opMonitorEnabled;
        this.metaservicesEnabled = metaservicesEnabled;
        this.serverProxyUrl = serverProxyUrl;
    }

    private Map<String, ServiceId.Conf> activeServiceIdsMap() {
        var cached = activeServiceIdsCache;
        if (cached != null) {
            return cached;
        }
        try {
            var built = buildActiveServiceIds(serverConfProvider,
                    proxyMonitorEnabled, opMonitorEnabled, metaservicesEnabled);
            activeServiceIdsCache = built;
            return built;
        } catch (RuntimeException e) {
            log.debug("Built-in service catalog not ready: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * Returns the list of active built-in service IDs in a stable, deterministic order.
     */
    List<ServiceId.Conf> activeServiceIds() {
        return List.copyOf(activeServiceIdsMap().values());
    }

    /**
     * Looks up a built-in ServiceId by its encoded asset ID string.
     *
     * @param assetId the encoded asset ID
     * @return the matching ServiceId.Conf, or null if not a known built-in asset
     */
    @Nullable
    ServiceId.Conf findServiceId(String assetId) {
        if (assetId == null || assetId.isBlank()) {
            return null;
        }
        return activeServiceIdsMap().get(assetId);
    }

    /**
     * Returns the server-proxy base URL for built-in data addresses.
     */
    String serverProxyUrl() {
        return serverProxyUrl;
    }

    private static Map<String, ServiceId.Conf> buildActiveServiceIds(ServerConfProvider serverConfProvider,
                                                                      boolean proxyMonitorEnabled,
                                                                      boolean opMonitorEnabled,
                                                                      boolean metaservicesEnabled) {
        var owner = serverConfProvider.getIdentifier().getOwner();
        var entries = new java.util.LinkedHashMap<String, ServiceId.Conf>();

        addIfEnabled(entries, proxyMonitorEnabled, owner, PROXY_MONITOR_SERVICE_CODE);

        addIfEnabled(entries, opMonitorEnabled, owner, OP_MONITOR_OPERATIONAL_DATA_SERVICE_CODE);
        addIfEnabled(entries, opMonitorEnabled, owner, OP_MONITOR_HEALTH_DATA_SERVICE_CODE);

        addIfEnabled(entries, metaservicesEnabled, owner, META_LIST_METHODS_SERVICE_CODE);
        addIfEnabled(entries, metaservicesEnabled, owner, META_ALLOWED_METHODS_SERVICE_CODE);
        addIfEnabled(entries, metaservicesEnabled, owner, META_GET_WSDL_SERVICE_CODE);
        addIfEnabled(entries, metaservicesEnabled, owner, META_GET_OPEN_API_SERVICE_CODE);

        log.debug("Built-in service catalog active entries: {}", entries.keySet());
        return Map.copyOf(entries);
    }

    private static void addIfEnabled(Map<String, ServiceId.Conf> entries, boolean enabled,
                                     ee.ria.xroad.common.identifier.ClientId owner, String serviceCode) {
        if (!enabled) {
            return;
        }
        var serviceId = ServiceId.Conf.create(
                owner.getXRoadInstance(), owner.getMemberClass(), owner.getMemberCode(),
                null, serviceCode);
        entries.put(serviceId.asEncodedId(), serviceId);
    }

    /**
     * Returns whether any built-in services are active.
     */
    boolean hasActiveServices() {
        return !activeServiceIdsMap().isEmpty();
    }

    /**
     * Returns a predicate that matches asset IDs belonging to built-in services.
     */
    Predicate<String> isBuiltinAssetId() {
        return assetId -> activeServiceIdsMap().containsKey(assetId);
    }
}
