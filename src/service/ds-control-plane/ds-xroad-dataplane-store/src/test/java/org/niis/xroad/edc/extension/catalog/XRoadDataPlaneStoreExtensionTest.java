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
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.edc.protocol.assetaccess.XRoadTransferType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.edc.participantcontext.spi.types.ParticipantResource.queryByParticipantContextId;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XRoadDataPlaneStoreExtensionTest {

    private static final String MEMBER_CONTEXT = "DEV:COM:222";

    @Mock
    private ServiceExtensionContext context;

    private XRoadDataPlaneStoreExtension extension;

    @BeforeEach
    void setUp() {
        extension = new XRoadDataPlaneStoreExtension();
    }

    @Test
    void providedStoreResolvesConfiguredEntryForAnyContextWithoutRegistration() {
        when(context.getConfig("xroad.cp.dataplane")).thenReturn(buildDataplaneConfig(Map.of(
                "xroad.cp.dataplane.proxy.id", "xroad-proxy",
                "xroad.cp.dataplane.proxy.url", "http://127.0.0.1:5590/full/api/v1/dataflows",
                "xroad.cp.dataplane.proxy.allowed-source-types", "http",
                "xroad.cp.dataplane.proxy.allowed-transfer-types", XRoadTransferType.PULL.wireValue()
        )));

        extension.initialize(context);
        var store = extension.dataPlaneInstanceStore();

        var instance = store.findById("xroad-proxy::" + MEMBER_CONTEXT);
        assertThat(instance).isNotNull();
        assertThat(instance.getParticipantContextId()).isEqualTo(MEMBER_CONTEXT);
        assertThat(instance.getUrl()).hasToString("http://127.0.0.1:5590/full/api/v1/dataflows");
        assertThat(instance.getAllowedSourceTypes()).containsExactly("http");
        assertThat(instance.getAllowedTransferTypes()).containsExactly(XRoadTransferType.PULL.wireValue());
        assertThat(instance.getState()).isEqualTo(DataPlaneInstanceStates.REGISTERED.code());
        assertThat(store.query(queryByParticipantContextId("ss1").build()))
                .extracting(DataPlaneInstance::getId)
                .containsExactly("xroad-proxy::ss1");
    }

    @Test
    void providedStoreSkipsDisabledEntries() {
        when(context.getConfig("xroad.cp.dataplane")).thenReturn(buildDataplaneConfig(Map.of(
                "xroad.cp.dataplane.proxy.id", "xroad-proxy",
                "xroad.cp.dataplane.proxy.url", "http://127.0.0.1:5590/full/api/v1/dataflows",
                "xroad.cp.dataplane.proxy.enabled", "false"
        )));

        extension.initialize(context);
        var store = extension.dataPlaneInstanceStore();

        assertThat(store.findById("xroad-proxy::" + MEMBER_CONTEXT)).isNull();
        assertThat(store.query(queryByParticipantContextId(MEMBER_CONTEXT).build())).isEmpty();
    }

    @Test
    void providedStoreServesEveryEntryOfMultipleEntryConfiguration() {
        when(context.getConfig("xroad.cp.dataplane")).thenReturn(buildDataplaneConfig(Map.of(
                "xroad.cp.dataplane.dp1.id", "dp1",
                "xroad.cp.dataplane.dp1.url", "http://dp1:5590",
                "xroad.cp.dataplane.dp1.allowed-transfer-types", XRoadTransferType.PULL.wireValue(),
                "xroad.cp.dataplane.dp2.id", "dp2",
                "xroad.cp.dataplane.dp2.url", "http://dp2:5591",
                "xroad.cp.dataplane.dp2.allowed-transfer-types", XRoadTransferType.PUSH.wireValue()
        )));

        extension.initialize(context);

        assertThat(extension.dataPlaneInstanceStore().query(queryByParticipantContextId(MEMBER_CONTEXT).build()))
                .extracting(DataPlaneInstance::getId)
                .containsExactlyInAnyOrder("dp1::" + MEMBER_CONTEXT, "dp2::" + MEMBER_CONTEXT);
    }

    @Test
    void providedStoreIsAvailableAndEmptyWhenNoEntriesConfigured() {
        when(context.getConfig("xroad.cp.dataplane")).thenReturn(ConfigFactory.empty());

        extension.initialize(context);
        var store = extension.dataPlaneInstanceStore();

        assertThat(store).isNotNull();
        assertThat(store.findById("xroad-proxy::" + MEMBER_CONTEXT)).isNull();
        assertThat(store.query(queryByParticipantContextId(MEMBER_CONTEXT).build())).isEmpty();
    }

    @Test
    void initializeRejectsEntryIdContainingSeparator() {
        when(context.getConfig("xroad.cp.dataplane")).thenReturn(buildDataplaneConfig(Map.of(
                "xroad.cp.dataplane.bad-entry.id", "xroad::proxy",
                "xroad.cp.dataplane.bad-entry.url", "http://127.0.0.1:5590/full/api/v1/dataflows"
        )));

        assertThatThrownBy(() -> extension.initialize(context))
                .isInstanceOf(EdcException.class)
                .hasMessageContaining("bad-entry")
                .hasMessageContaining("xroad::proxy");
    }

    @Test
    void initializeRejectsDuplicateEntryIds() {
        when(context.getConfig("xroad.cp.dataplane")).thenReturn(buildDataplaneConfig(Map.of(
                "xroad.cp.dataplane.first.id", "xroad-proxy",
                "xroad.cp.dataplane.first.url", "http://127.0.0.1:5590/full/api/v1/dataflows",
                "xroad.cp.dataplane.second.id", "xroad-proxy",
                "xroad.cp.dataplane.second.url", "http://proxy:5590/full/api/v1/dataflows"
        )));

        assertThatThrownBy(() -> extension.initialize(context))
                .isInstanceOf(EdcException.class)
                .hasMessageContaining("first")
                .hasMessageContaining("second")
                .hasMessageContaining("xroad-proxy");
    }

    @Test
    void storeIsProvidedAsNonDefaultProviderSoItWinsOverEdcInMemoryDefault() throws Exception {
        var provider = XRoadDataPlaneStoreExtension.class.getMethod("dataPlaneInstanceStore")
                .getAnnotation(Provider.class);

        assertThat(provider).isNotNull();
        assertThat(provider.isDefault()).isFalse();
        assertThat(XRoadDataPlaneStoreExtension.class.getMethod("dataPlaneInstanceStore").getReturnType())
                .isEqualTo(DataPlaneInstanceStore.class);
    }

    private static Config buildDataplaneConfig(Map<String, String> entries) {
        return ConfigFactory.fromMap(entries).getConfig("xroad.cp.dataplane");
    }
}
