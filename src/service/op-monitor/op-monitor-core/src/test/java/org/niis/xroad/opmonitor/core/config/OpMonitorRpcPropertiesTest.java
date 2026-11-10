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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.CommonRpcConfigKeys;
import org.niis.xroad.common.properties.config.keys.OpMonitorConfigKeys;
import org.niis.xroad.common.rpc.RpcProperties;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpMonitorRpcPropertiesTest {

    @Test
    void identityIsOpenbaoByDefault() {
        var properties = new OpMonitorRpcProperties(config(Map.of()));

        assertThat(properties.identitySource()).isEqualTo(RpcProperties.IdentitySource.OPENBAO);
    }

    @Test
    void fileIdentityWithoutAnyPathNamesAllThreeSettings() {
        var properties = new OpMonitorRpcProperties(config(Map.of()));

        assertThatThrownBy(properties::fileIdentity)
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining(OpMonitorConfigKeys.RPC_FILE_IDENTITY_PRIVATE_KEY_PATH.key())
                .hasMessageContaining(OpMonitorConfigKeys.RPC_FILE_IDENTITY_CERTIFICATE_PATH.key())
                .hasMessageContaining(OpMonitorConfigKeys.RPC_FILE_IDENTITY_TRUSTED_CA_PATH.key());
    }

    @ParameterizedTest
    @MethodSource("missingPathSettings")
    void fileIdentityWithOneMissingPathNamesOnlyThatSetting(String missing, List<String> present) {
        var overrides = new HashMap<String, String>();
        present.forEach(key -> overrides.put(key, "/id/" + key));
        overrides.put(missing, "");
        var properties = new OpMonitorRpcProperties(config(overrides));

        var failure = assertThatThrownBy(properties::fileIdentity)
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining(missing);
        present.forEach(failure::hasMessageNotContaining);
    }

    static Stream<Arguments> missingPathSettings() {
        var privateKey = OpMonitorConfigKeys.RPC_FILE_IDENTITY_PRIVATE_KEY_PATH.key();
        var certificate = OpMonitorConfigKeys.RPC_FILE_IDENTITY_CERTIFICATE_PATH.key();
        var trustedCa = OpMonitorConfigKeys.RPC_FILE_IDENTITY_TRUSTED_CA_PATH.key();
        return Stream.of(
                Arguments.of(privateKey, List.of(certificate, trustedCa)),
                Arguments.of(certificate, List.of(privateKey, trustedCa)),
                Arguments.of(trustedCa, List.of(privateKey, certificate)));
    }

    @Test
    void identitySourceFileResolvesInAnyCase() {
        for (var source : new String[] {"file", "FILE", "File"}) {
            var properties = new OpMonitorRpcProperties(config(Map.of("xroad.op-monitor.rpc.identity-source", source)));

            assertThat(properties.identitySource()).isEqualTo(RpcProperties.IdentitySource.FILE);
        }
    }

    @Test
    void fileIdentityPathsAreReadFromTheOpMonitorKeys() {
        var properties = new OpMonitorRpcProperties(config(Map.of(
                "xroad.op-monitor.rpc.file-identity.private-key-path", "/id/server.key",
                "xroad.op-monitor.rpc.file-identity.certificate-path", "/id/server.crt",
                "xroad.op-monitor.rpc.file-identity.trusted-ca-path", "/id/trusted-ca.crt")));

        assertThat(properties.fileIdentity()).isEqualTo(new RpcProperties.FileIdentity(
                Path.of("/id/server.key"), Path.of("/id/server.crt"), Path.of("/id/trusted-ca.crt")));
    }

    @Test
    void sharedRpcSettingsStillComeFromTheCommonRpcKeys() {
        var properties = new OpMonitorRpcProperties(config(Map.of("xroad.common-rpc.use-tls", "false")));

        assertThat(properties.useTls()).isFalse();
    }

    private static XRoadConfig config(Map<String, String> overrides) {
        return XRoadConfigBuilder.create()
                .register(CommonRpcConfigKeys.instance())
                .register(OpMonitorConfigKeys.instance())
                .overrides(overrides)
                .build();
    }
}
