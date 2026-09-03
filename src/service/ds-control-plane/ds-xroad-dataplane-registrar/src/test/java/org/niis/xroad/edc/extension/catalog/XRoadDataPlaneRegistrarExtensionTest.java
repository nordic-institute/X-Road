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

import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance;
import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstanceStates;
import org.eclipse.edc.connector.dataplane.selector.spi.store.DataPlaneInstanceStore;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.edc.protocol.assetaccess.XRoadTransferType;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XRoadDataPlaneRegistrarExtensionTest {

    private static final String HOST_CONTEXT = "ss1";
    private static final String MGMT_CONTEXT = "ss1-mgmt";
    private static final String MEMBER_CONTEXT = "ss1:CLASS:MEMBER";

    @Mock
    private ServiceExtensionContext context;

    @Mock
    private DataPlaneInstanceStore store;

    @Mock
    private ParticipantContextService participantContextService;

    private XRoadDataPlaneRegistrarExtension extension;

    @BeforeEach
    void setUp() throws Exception {
        extension = new XRoadDataPlaneRegistrarExtension();
        setField(extension, "dataPlaneInstanceStore", store);
        setField(extension, "participantContextService", participantContextService);
    }

    @Test
    void initializeRegistersConfiguredEntryUnderEachParticipantContext() {
        stubParticipantContexts();
        when(context.getConfig("xroad.cp.dataplane")).thenReturn(buildDataplaneConfig(Map.of(
                "xroad.cp.dataplane.proxy.id", "xroad-proxy",
                "xroad.cp.dataplane.proxy.url", "http://127.0.0.1:5590/full/api/v1/dataflows",
                "xroad.cp.dataplane.proxy.allowed-source-types", "http",
                "xroad.cp.dataplane.proxy.allowed-transfer-types", XRoadTransferType.PULL.wireValue()
        )));
        when(store.save(any())).thenReturn(StoreResult.success());

        extension.initialize(context);

        var captor = ArgumentCaptor.forClass(DataPlaneInstance.class);
        verify(store, times(2)).save(captor.capture());
        var saved = captor.getAllValues();

        assertThat(saved).extracting(DataPlaneInstance::getId)
                .containsExactlyInAnyOrder("xroad-proxy::" + HOST_CONTEXT, "xroad-proxy::" + MGMT_CONTEXT);
        assertThat(saved).extracting(DataPlaneInstance::getParticipantContextId)
                .containsExactlyInAnyOrder(HOST_CONTEXT, MGMT_CONTEXT);
        assertThat(saved).allSatisfy(instance -> {
            assertThat(instance.getUrl()).hasToString("http://127.0.0.1:5590/full/api/v1/dataflows");
            assertThat(instance.getAllowedSourceTypes()).containsExactly("http");
            assertThat(instance.getAllowedTransferTypes()).containsExactly(XRoadTransferType.PULL.wireValue());
            assertThat(instance.getState()).isEqualTo(DataPlaneInstanceStates.REGISTERED.code());
        });
    }

    @Test
    void initializeSkipsDisabledEntries() {
        stubParticipantContexts();
        when(context.getConfig("xroad.cp.dataplane")).thenReturn(buildDataplaneConfig(Map.of(
                "xroad.cp.dataplane.proxy.id", "xroad-proxy",
                "xroad.cp.dataplane.proxy.url", "http://127.0.0.1:5590/full/api/v1/dataflows",
                "xroad.cp.dataplane.proxy.enabled", "false"
        )));

        extension.initialize(context);

        verify(store, never()).save(any());
    }

    @Test
    void initializeSplitsCsvAllowedTypes() {
        stubParticipantContexts();
        when(context.getConfig("xroad.cp.dataplane")).thenReturn(buildDataplaneConfig(Map.of(
                "xroad.cp.dataplane.dp1.id", "dp1",
                "xroad.cp.dataplane.dp1.url", "http://dp1:5590",
                "xroad.cp.dataplane.dp1.allowed-source-types", "http,https",
                "xroad.cp.dataplane.dp1.allowed-transfer-types",
                XRoadTransferType.PULL.wireValue() + ", " + XRoadTransferType.PUSH.wireValue()
        )));
        when(store.save(any())).thenReturn(StoreResult.success());

        extension.initialize(context);

        var captor = ArgumentCaptor.forClass(DataPlaneInstance.class);
        verify(store, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(instance -> {
            assertThat(instance.getAllowedSourceTypes()).containsExactlyInAnyOrder("http", "https");
            assertThat(instance.getAllowedTransferTypes())
                    .containsExactlyInAnyOrder(XRoadTransferType.PULL.wireValue(), XRoadTransferType.PUSH.wireValue());
        });
    }

    @Test
    void initializeWithMultipleEntriesRegistersAllUnderEachContext() {
        stubParticipantContexts();
        when(context.getConfig("xroad.cp.dataplane")).thenReturn(buildDataplaneConfig(Map.of(
                "xroad.cp.dataplane.dp1.id", "dp1",
                "xroad.cp.dataplane.dp1.url", "http://dp1:5590",
                "xroad.cp.dataplane.dp1.allowed-transfer-types", XRoadTransferType.PULL.wireValue(),
                "xroad.cp.dataplane.dp2.id", "dp2",
                "xroad.cp.dataplane.dp2.url", "http://dp2:5591",
                "xroad.cp.dataplane.dp2.allowed-transfer-types", XRoadTransferType.PUSH.wireValue()
        )));
        when(store.save(any())).thenReturn(StoreResult.success());

        extension.initialize(context);

        verify(store, times(4)).save(any());
    }

    @Test
    void initializeWithNoEntriesDoesNotCallStore() {
        when(context.getConfig("xroad.cp.dataplane")).thenReturn(ConfigFactory.empty());

        extension.initialize(context);
        extension.start();

        verify(store, never()).save(any());
        verify(participantContextService, never()).search(any());
    }

    @Test
    void initializeNeverQueriesPersistedParticipantContextStore() {
        stubParticipantContexts();
        when(context.getConfig("xroad.cp.dataplane")).thenReturn(buildDataplaneConfig(Map.of(
                "xroad.cp.dataplane.proxy.id", "xroad-proxy",
                "xroad.cp.dataplane.proxy.url", "http://127.0.0.1:5590/full/api/v1/dataflows"
        )));
        when(store.save(any())).thenReturn(StoreResult.success());

        extension.initialize(context);

        verifyNoInteractions(participantContextService);
    }

    @Test
    void startWithEmptyPersistedStoreRegistersOnlyLegacyPair() {
        stubParticipantContexts();
        stubPersistedParticipantContexts();
        when(context.getConfig("xroad.cp.dataplane")).thenReturn(buildDataplaneConfig(Map.of(
                "xroad.cp.dataplane.proxy.id", "xroad-proxy",
                "xroad.cp.dataplane.proxy.url", "http://127.0.0.1:5590/full/api/v1/dataflows"
        )));
        when(store.save(any())).thenReturn(StoreResult.success());

        extension.initialize(context);
        extension.start();

        var captor = ArgumentCaptor.forClass(DataPlaneInstance.class);
        verify(store, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(DataPlaneInstance::getParticipantContextId)
                .containsExactlyInAnyOrder(HOST_CONTEXT, MGMT_CONTEXT);
    }

    @Test
    void startReconcilesPersistedParticipantContexts() {
        stubParticipantContexts();
        stubPersistedParticipantContexts(participantContext(MEMBER_CONTEXT), participantContext(HOST_CONTEXT));
        when(context.getConfig("xroad.cp.dataplane")).thenReturn(buildDataplaneConfig(Map.of(
                "xroad.cp.dataplane.proxy.id", "xroad-proxy",
                "xroad.cp.dataplane.proxy.url", "http://127.0.0.1:5590/full/api/v1/dataflows"
        )));
        when(store.save(any())).thenReturn(StoreResult.success());

        extension.initialize(context);
        extension.start();

        var captor = ArgumentCaptor.forClass(DataPlaneInstance.class);
        verify(store, times(3)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(DataPlaneInstance::getParticipantContextId)
                .containsExactlyInAnyOrder(HOST_CONTEXT, MGMT_CONTEXT, MEMBER_CONTEXT);
    }

    @Test
    void startKeepsServingConfiguredContextsWhenPersistedContextEnumerationFails() {
        stubParticipantContexts();
        when(context.getConfig("xroad.cp.dataplane")).thenReturn(buildDataplaneConfig(Map.of(
                "xroad.cp.dataplane.proxy.id", "xroad-proxy",
                "xroad.cp.dataplane.proxy.url", "http://127.0.0.1:5590/full/api/v1/dataflows"
        )));
        when(store.save(any())).thenReturn(StoreResult.success());
        when(participantContextService.search(any(QuerySpec.class)))
                .thenReturn(ServiceResult.unexpected("relation \"participant_context\" does not exist"));

        extension.initialize(context);

        assertThatCode(extension::start).doesNotThrowAnyException();

        var captor = ArgumentCaptor.forClass(DataPlaneInstance.class);
        verify(store, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(DataPlaneInstance::getParticipantContextId)
                .containsExactlyInAnyOrder(HOST_CONTEXT, MGMT_CONTEXT);
    }

    @Test
    void providedRegistrarRegistersHookedContextInProcess() {
        stubParticipantContexts();
        when(context.getConfig("xroad.cp.dataplane")).thenReturn(buildDataplaneConfig(Map.of(
                "xroad.cp.dataplane.proxy.id", "xroad-proxy",
                "xroad.cp.dataplane.proxy.url", "http://127.0.0.1:5590/full/api/v1/dataflows"
        )));
        when(store.save(any())).thenReturn(StoreResult.success());
        extension.initialize(context);

        var registrar = extension.dataPlaneContextRegistrar();
        registrar.registerParticipantContext(MEMBER_CONTEXT);

        var captor = ArgumentCaptor.forClass(DataPlaneInstance.class);
        verify(store, times(3)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(DataPlaneInstance::getId)
                .contains("xroad-proxy::" + MEMBER_CONTEXT);
    }

    @Test
    void registrarHookIsIdempotentOnRepeatCalls() {
        stubParticipantContexts();
        when(context.getConfig("xroad.cp.dataplane")).thenReturn(buildDataplaneConfig(Map.of(
                "xroad.cp.dataplane.proxy.id", "xroad-proxy",
                "xroad.cp.dataplane.proxy.url", "http://127.0.0.1:5590/full/api/v1/dataflows"
        )));
        when(store.save(any())).thenReturn(StoreResult.success());
        extension.initialize(context);

        var registrar = extension.dataPlaneContextRegistrar();
        registrar.registerParticipantContext(MEMBER_CONTEXT);
        registrar.registerParticipantContext(MEMBER_CONTEXT);

        var captor = ArgumentCaptor.forClass(DataPlaneInstance.class);
        verify(store, times(4)).save(captor.capture());
        assertThat(captor.getAllValues())
                .filteredOn(instance -> instance.getParticipantContextId().equals(MEMBER_CONTEXT))
                .extracting(DataPlaneInstance::getId)
                .containsOnly("xroad-proxy::" + MEMBER_CONTEXT);
    }

    private void stubParticipantContexts() {
        when(context.getSetting("edc.hostname", "localhost")).thenReturn(HOST_CONTEXT);
        when(context.getSetting("xroad.dsp.participant-context-id", HOST_CONTEXT)).thenReturn(HOST_CONTEXT);
        when(context.getSetting("xroad.dsp.management-participant-context-id", HOST_CONTEXT + "-mgmt"))
                .thenReturn(MGMT_CONTEXT);
    }

    private void stubPersistedParticipantContexts(ParticipantContext... persisted) {
        when(participantContextService.search(any(QuerySpec.class)))
                .thenReturn(ServiceResult.success(List.of(persisted)));
    }

    private static ParticipantContext participantContext(String participantContextId) {
        return ParticipantContext.Builder.newInstance()
                .participantContextId(participantContextId)
                .identity("did:web:example.com:" + participantContextId)
                .build();
    }

    private static Config buildDataplaneConfig(Map<String, String> entries) {
        return ConfigFactory.fromMap(entries).getConfig("xroad.cp.dataplane");
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
