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
import org.eclipse.edc.connector.dataplane.selector.spi.store.DataPlaneInstanceStore;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.util.List;
import java.util.stream.Stream;

import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PARTICIPANT_CONTEXT_FAILED;

/**
 * Populates the EDC {@link DataPlaneInstanceStore} from local YAML configuration at boot, and exposes a
 * {@link DataPlaneContextRegistrar} hook so other extensions can register data-plane instances for
 * participant contexts created afterwards, at runtime.
 *
 * <p>At boot, this extension registers the configured legacy pair (host and management contexts). At
 * {@code start()} — after every extension's SQL schema has been bootstrapped in the {@code prepare()}
 * phase, but before any extension's {@code start()} runs — it reconciles: it enumerates every
 * participant context already persisted in the {@link ParticipantContextService} store — regardless of
 * kind — and registers instances for those too. This covers the case where the in-memory
 * {@link DataPlaneInstanceStore} was wiped by a restart while the participant-context store survived it.
 * The enumeration is deferred out of {@code initialize()} because the SQL-backed participant-context
 * store's schema does not exist yet at that point on a fresh database.</p>
 */
@Slf4j
@Provides(DataPlaneContextRegistrar.class)
@Extension(XRoadDataPlaneRegistrarExtension.NAME)
public class XRoadDataPlaneRegistrarExtension implements ServiceExtension {

    static final String NAME = "X-Road DataPlane Registrar";
    static final String SETTING_DATAPLANES = "xroad.cp.dataplane";
    static final String SETTING_HOSTNAME = "edc.hostname";
    static final String SETTING_PARTICIPANT_CONTEXT_ID = "xroad.dsp.participant-context-id";
    static final String SETTING_MANAGEMENT_PARTICIPANT_CONTEXT_ID = "xroad.dsp.management-participant-context-id";
    static final String DEFAULT_HOSTNAME = "localhost";
    static final String MANAGEMENT_CONTEXT_SUFFIX = "-mgmt";

    @Inject
    private DataPlaneInstanceStore dataPlaneInstanceStore;

    @Inject
    private ParticipantContextService participantContextService;

    private DataPlaneContextRegistrar registrar;
    private List<String> legacyContextIds;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var dataplanesConfig = context.getConfig(SETTING_DATAPLANES);
        var entries = dataplanesConfig.partition().toList();
        registrar = new DefaultDataPlaneContextRegistrar(entries, dataPlaneInstanceStore);

        if (entries.isEmpty()) {
            log.warn("No data plane entries configured under '{}' — control plane will advertise no transfer endpoints.",
                    SETTING_DATAPLANES);
            return;
        }

        legacyContextIds = resolveParticipantContextIds(context);
        legacyContextIds.forEach(registrar::registerParticipantContext);
    }

    /**
     * Reconciles data-plane instances from the persisted participant-context store. Runs in {@code start()},
     * not {@code initialize()}: the SQL-backed participant-context store's schema is only guaranteed to exist
     * once every extension's {@code prepare()} phase has completed, which happens strictly before any
     * extension's {@code start()}.
     */
    @Override
    public void start() {
        if (legacyContextIds == null) {
            return;
        }
        reconcilePersistedParticipantContexts();
    }

    /**
     * Exposes the hook other extensions call to register a data-plane instance for a participant context
     * created at runtime.
     */
    @Provider
    public DataPlaneContextRegistrar dataPlaneContextRegistrar() {
        return registrar;
    }

    private void reconcilePersistedParticipantContexts() {
        var result = participantContextService.search(QuerySpec.max());
        if (result.failed()) {
            throw XrdRuntimeException.systemException(DSP_PARTICIPANT_CONTEXT_FAILED,
                    "Failed to enumerate persisted participant contexts for boot reconcile: %s", result.getFailureDetail());
        }
        result.getContent().stream()
                .map(ParticipantContext::getParticipantContextId)
                .filter(participantContextId -> !legacyContextIds.contains(participantContextId))
                .forEach(registrar::registerParticipantContext);
    }

    private List<String> resolveParticipantContextIds(ServiceExtensionContext context) {
        var defaultContextId = context.getSetting(SETTING_HOSTNAME, DEFAULT_HOSTNAME);
        var participantContextId = context.getSetting(SETTING_PARTICIPANT_CONTEXT_ID, defaultContextId);
        var managementParticipantContextId = context.getSetting(
                SETTING_MANAGEMENT_PARTICIPANT_CONTEXT_ID, participantContextId + MANAGEMENT_CONTEXT_SUFFIX);
        return Stream.of(participantContextId, managementParticipantContextId).distinct().toList();
    }
}
