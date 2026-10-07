/*
 * The MIT License
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
package org.niis.xroad.proxy.core.configuration;

import io.grpc.ManagedChannel;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.CommonRpcConfigKeys;
import org.niis.xroad.common.properties.config.keys.ProxyConfigKeys;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.opmonitor.api.OpMonitoringBuffer;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.proxy.core.addon.opmonitoring.NoOpMonitoringBuffer;
import org.niis.xroad.proxy.core.addon.opmonitoring.OpMonitoringBufferImpl;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpMonitoringBufferInitializerTest {
    private final ProxyConfig.OpMonitoringBufferInitializer initializer = new ProxyConfig.OpMonitoringBufferInitializer();
    private final RpcChannelFactory rpcChannelFactory = mock(RpcChannelFactory.class);
    private final ManagedChannel channel = mock(ManagedChannel.class);

    @Test
    void noOpMonitorClientOrChannelIsCreatedWhenOperationalMonitoringIsDisabled() {
        var buffer = create(Map.of());

        buffer.store(new OpMonitoringData(OpMonitoringData.SecurityServerType.CLIENT, 1));

        assertThat(buffer).isInstanceOf(NoOpMonitoringBuffer.class);
        verify(rpcChannelFactory, never()).createChannel(any());
    }

    @Test
    void noOpMonitorClientOrChannelIsCreatedWhenBufferIsSwitchedOff() {
        var buffer = create(Map.of("xroad.proxy.addon.op-monitor.enabled", "true",
                "xroad.proxy.addon.op-monitor.buffer.size", "0"));

        buffer.store(new OpMonitoringData(OpMonitoringData.SecurityServerType.CLIENT, 1));

        assertThat(buffer).isInstanceOf(OpMonitoringBufferImpl.class);
        verify(rpcChannelFactory, never()).createChannel(any());
        initializer.cleanup(buffer);
    }

    @Test
    void opMonitorChannelIsNotCreatedAtStartupWhenOperationalMonitoringIsEnabled() {
        var buffer = create(Map.of("xroad.proxy.addon.op-monitor.enabled", "true"));

        verify(rpcChannelFactory, never()).createChannel(any());
        initializer.cleanup(buffer);
    }

    @Test
    void opMonitorChannelIsCreatedOnceOnFirstStoreAndClosedOnShutdown() {
        when(rpcChannelFactory.createChannel(any())).thenReturn(channel);
        var buffer = create(Map.of("xroad.proxy.addon.op-monitor.enabled", "true"));

        buffer.store(new OpMonitoringData(OpMonitoringData.SecurityServerType.CLIENT, 1));
        buffer.store(new OpMonitoringData(OpMonitoringData.SecurityServerType.CLIENT, 2));

        Awaitility.await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> verify(rpcChannelFactory, times(1)).createChannel(any()));
        initializer.cleanup(buffer);
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> verify(channel).shutdown());
    }

    private OpMonitoringBuffer create(Map<String, String> overrides) {
        XRoadConfig config = XRoadConfigBuilder.create()
                .register(ProxyConfigKeys.instance())
                .register(CommonRpcConfigKeys.instance())
                .overrides(overrides)
                .build();
        return initializer.opMonitoringBuffer(mock(ServerConfProvider.class), new ProxyProperties(config),
                rpcChannelFactory, config);
    }
}
