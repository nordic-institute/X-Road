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
import org.eclipse.edc.connector.dataplane.selector.spi.store.DataPlaneInstanceStore;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XRoadDataPlaneRegistrarExtensionTest {

    @Mock
    private ServiceExtensionContext context;

    @Mock
    private DataPlaneInstanceStore store;

    private XRoadDataPlaneRegistrarExtension extension;

    @BeforeEach
    void setUp() throws Exception {
        extension = new XRoadDataPlaneRegistrarExtension();
        setField(extension, "dataPlaneInstanceStore", store);
    }

    @Test
    void initializeRegistersConfiguredEntries() {
        when(context.getConfig("xroad.cp.dataplane")).thenReturn(buildDataplaneConfig(Map.of(
                "xroad.cp.dataplane.proxy.id", "xroad-proxy-ss0",
                "xroad.cp.dataplane.proxy.url", "http://127.0.0.1:5590/full/api/v1/dataflows",
                "xroad.cp.dataplane.proxy.allowed-source-types", "http",
                "xroad.cp.dataplane.proxy.allowed-transfer-types", XRoadTransferType.PULL.wireValue()
        )));
        when(store.save(any())).thenReturn(StoreResult.success());

        extension.initialize(context);

        var captor = ArgumentCaptor.forClass(DataPlaneInstance.class);
        verify(store).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo("xroad-proxy-ss0");
        assertThat(saved.getUrl()).hasToString("http://127.0.0.1:5590/full/api/v1/dataflows");
        assertThat(saved.getAllowedSourceTypes()).containsExactly("http");
        assertThat(saved.getAllowedTransferTypes()).containsExactly(XRoadTransferType.PULL.wireValue());
    }

    @Test
    void initializeSkipsDisabledEntries() {
        when(context.getConfig("xroad.cp.dataplane")).thenReturn(buildDataplaneConfig(Map.of(
                "xroad.cp.dataplane.proxy.id", "xroad-proxy-ss0",
                "xroad.cp.dataplane.proxy.url", "http://127.0.0.1:5590/full/api/v1/dataflows",
                "xroad.cp.dataplane.proxy.enabled", "false"
        )));

        extension.initialize(context);

        verify(store, never()).save(any());
    }

    @Test
    void initializeSplitsCsvAllowedTypes() {
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
        verify(store).save(captor.capture());
        assertThat(captor.getValue().getAllowedSourceTypes()).containsExactlyInAnyOrder("http", "https");
        assertThat(captor.getValue().getAllowedTransferTypes())
                .containsExactlyInAnyOrder(XRoadTransferType.PULL.wireValue(), XRoadTransferType.PUSH.wireValue());
    }

    @Test
    void initializeWithMultipleEntriesRegistersAll() {
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

        verify(store, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void initializeWithNoEntriesDoesNotCallStore() {
        when(context.getConfig("xroad.cp.dataplane")).thenReturn(ConfigFactory.empty());

        extension.initialize(context);

        verify(store, never()).save(any());
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
