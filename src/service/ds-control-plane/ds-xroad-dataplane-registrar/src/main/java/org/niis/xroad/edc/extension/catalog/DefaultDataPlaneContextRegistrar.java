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

import lombok.extern.slf4j.Slf4j;
import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance;
import org.eclipse.edc.connector.dataplane.selector.spi.store.DataPlaneInstanceStore;
import org.eclipse.edc.spi.system.configuration.Config;

import java.util.Arrays;
import java.util.List;

/**
 * Registers the control plane's configured data-plane entries under participant contexts, backed by the
 * {@link DataPlaneInstanceStore}. Every {@link DataPlaneInstanceStore#save(Object)} call follows upsert
 * semantics, so registering the same (entry, participant context) pair repeatedly is safe.
 */
@Slf4j
final class DefaultDataPlaneContextRegistrar implements DataPlaneContextRegistrar {

    static final String KEY_ID = "id";
    static final String KEY_URL = "url";
    static final String KEY_ENABLED = "enabled";
    static final String KEY_ALLOWED_SOURCE_TYPES = "allowed-source-types";
    static final String KEY_ALLOWED_TRANSFER_TYPES = "allowed-transfer-types";

    private final List<Config> entries;
    private final DataPlaneInstanceStore dataPlaneInstanceStore;

    DefaultDataPlaneContextRegistrar(List<Config> entries, DataPlaneInstanceStore dataPlaneInstanceStore) {
        this.entries = entries;
        this.dataPlaneInstanceStore = dataPlaneInstanceStore;
    }

    @Override
    public void registerParticipantContext(String participantContextId) {
        entries.forEach(entry -> register(entry, participantContextId));
    }

    private void register(Config entry, String participantContextId) {
        var node = entry.currentNode();
        boolean enabled = entry.getBoolean(KEY_ENABLED, true);
        if (!enabled) {
            log.info("Data plane entry '{}' is disabled — skipping.", node);
            return;
        }

        var instance = buildInstance(entry, participantContextId);
        var result = dataPlaneInstanceStore.save(instance);
        if (result.failed()) {
            log.error("Failed to register data plane '{}' (config node '{}'): {}",
                    instance.getId(), node, result.getFailureDetail());
            return;
        }
        log.info("Registered data plane '{}' for participant context '{}' from config (node '{}', url='{}')",
                instance.getId(), participantContextId, node, instance.getUrl());
    }

    private static DataPlaneInstance buildInstance(Config entry, String participantContextId) {
        var builder = DataPlaneInstance.Builder.newInstance()
                .id(instanceId(entry.getString(KEY_ID), participantContextId))
                .participantContextId(participantContextId)
                .url(entry.getString(KEY_URL));

        getMultiValues(entry, KEY_ALLOWED_SOURCE_TYPES).forEach(builder::allowedSourceType);
        getMultiValues(entry, KEY_ALLOWED_TRANSFER_TYPES).forEach(builder::allowedTransferType);

        var instance = builder.build();
        instance.transitionToRegistered();
        return instance;
    }

    private static String instanceId(String baseId, String participantContextId) {
        return baseId + "::" + participantContextId;
    }

    private static List<String> getMultiValues(Config entry, String key) {
        var value = entry.getString(key, "");
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
