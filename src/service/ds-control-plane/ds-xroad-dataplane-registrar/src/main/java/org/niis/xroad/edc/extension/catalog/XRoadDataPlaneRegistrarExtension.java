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
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.Config;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Populates the EDC {@link DataPlaneInstanceStore} from local YAML configuration at boot.
 */
@Slf4j
@Extension(XRoadDataPlaneRegistrarExtension.NAME)
public class XRoadDataPlaneRegistrarExtension implements ServiceExtension {

    static final String NAME = "X-Road DataPlane Registrar";
    static final String SETTING_DATAPLANES = "xroad.cp.dataplane";
    static final String SETTING_HOSTNAME = "edc.hostname";
    static final String SETTING_PARTICIPANT_CONTEXT_ID = "xroad.dsp.participant-context-id";
    static final String SETTING_MANAGEMENT_PARTICIPANT_CONTEXT_ID = "xroad.dsp.management-participant-context-id";
    static final String DEFAULT_HOSTNAME = "localhost";
    static final String MANAGEMENT_CONTEXT_SUFFIX = "-mgmt";

    static final String KEY_ID = "id";
    static final String KEY_URL = "url";
    static final String KEY_ENABLED = "enabled";
    static final String KEY_ALLOWED_SOURCE_TYPES = "allowed-source-types";
    static final String KEY_ALLOWED_TRANSFER_TYPES = "allowed-transfer-types";

    @Inject
    private DataPlaneInstanceStore dataPlaneInstanceStore;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var dataplanesConfig = context.getConfig(SETTING_DATAPLANES);
        var entries = dataplanesConfig.partition().toList();
        if (entries.isEmpty()) {
            log.warn("No data plane entries configured under '{}' — control plane will advertise no transfer endpoints.",
                    SETTING_DATAPLANES);
            return;
        }
        var participantContextIds = resolveParticipantContextIds(context);
        entries.forEach(entry -> registerFromConfig(entry, participantContextIds));
    }

    private List<String> resolveParticipantContextIds(ServiceExtensionContext context) {
        var defaultContextId = context.getSetting(SETTING_HOSTNAME, DEFAULT_HOSTNAME);
        var participantContextId = context.getSetting(SETTING_PARTICIPANT_CONTEXT_ID, defaultContextId);
        var managementParticipantContextId = context.getSetting(
                SETTING_MANAGEMENT_PARTICIPANT_CONTEXT_ID, participantContextId + MANAGEMENT_CONTEXT_SUFFIX);
        return Stream.of(participantContextId, managementParticipantContextId).distinct().toList();
    }

    private void registerFromConfig(Config entry, List<String> participantContextIds) {
        var node = entry.currentNode();
        boolean enabled = entry.getBoolean(KEY_ENABLED, true);
        if (!enabled) {
            log.info("Data plane entry '{}' is disabled — skipping.", node);
            return;
        }
        participantContextIds.forEach(participantContextId -> register(entry, node, participantContextId));
    }

    private void register(Config entry, String node, String participantContextId) {
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

    private DataPlaneInstance buildInstance(Config entry, String participantContextId) {
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
