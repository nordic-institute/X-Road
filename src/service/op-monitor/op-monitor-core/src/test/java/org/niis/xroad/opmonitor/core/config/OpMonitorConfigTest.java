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
package org.niis.xroad.opmonitor.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.niis.xroad.common.properties.config.DeploymentMode;
import org.niis.xroad.common.properties.config.Source;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.CommonRpcConfigKeys;
import org.niis.xroad.common.properties.config.keys.OpMonitorConfigKeys;
import org.niis.xroad.common.rpc.RpcProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpMonitorConfigTest {

    @TempDir
    private Path tempDir;

    @Test
    void settingsFileIsReadFromTheOpMonitorSettingsPath() {
        assertThat(OpMonitorConfig.SETTINGS_FILE).isEqualTo(Path.of("/etc/xroad/conf.d/op-monitor-settings.yaml"));
    }

    @Test
    void productionConfigurationResolvesAnOpMonitorKeyFromTheSettingsFile() throws IOException {
        var file = Files.writeString(tempDir.resolve("op-monitor-settings.yaml"),
                "xroad.op-monitor.rpc.listen-address: 10.1.2.3\n");

        var config = new OpMonitorConfig(file).xRoadConfig("op-monitor-daemon");

        var value = config.get(OpMonitorConfigKeys.RPC_LISTEN_ADDRESS);
        assertThat(value.value()).isEqualTo("10.1.2.3");
        assertThat(value.source()).isEqualTo(Source.FILE);
    }

    @Test
    void containerizedModeIgnoresTheSettingsFile() throws IOException {
        var file = Files.writeString(tempDir.resolve("op-monitor-settings.yaml"),
                "xroad.op-monitor.rpc.listen-address: 10.1.2.3\n");

        var config = new OpMonitorConfig(file).buildConfig("op-monitor-daemon", DeploymentMode.CONTAINERIZED);

        assertThat(config.get(OpMonitorConfigKeys.RPC_LISTEN_ADDRESS).source()).isEqualTo(Source.DEFAULT);
    }

    @Test
    void rpcPropertiesPickUpTheFileIdentitySourceFromTheConfiguration() {
        var rpcProperties = new OpMonitorConfig().rpcProperties(config(Map.of(
                "xroad.op-monitor.rpc.identity-source", "file")));

        assertThat(rpcProperties).isInstanceOf(OpMonitorRpcProperties.class);
        assertThat(rpcProperties.identitySource()).isEqualTo(RpcProperties.IdentitySource.FILE);
    }

    private static XRoadConfig config(Map<String, String> overrides) {
        return XRoadConfigBuilder.create()
                .register(CommonRpcConfigKeys.instance())
                .register(OpMonitorConfigKeys.instance())
                .overrides(overrides)
                .build();
    }
}
