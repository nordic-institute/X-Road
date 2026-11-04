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
import org.junit.jupiter.params.provider.ValueSource;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.CommonRpcConfigKeys;
import org.niis.xroad.common.properties.config.keys.OpMonitorConfigKeys;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpMonitorServerPropertiesTest {
    private static final String ALLOWED_CLIENT_NAMES = "xroad.op-monitor.rpc.allowed-client-names";

    @Test
    void maxInboundMessageSizeKeepsGrpcDefaultWhenNotConfigured() {
        var properties = new OpMonitorServerProperties(config(Map.of()));

        assertThat(properties.maxInboundMessageSize()).isEmpty();
    }

    @Test
    void maxInboundMessageSizeIsReadFromRpcKey() {
        var properties = new OpMonitorServerProperties(
                config(Map.of("xroad.op-monitor.rpc.max-inbound-message-size", "8388608")));

        assertThat(properties.maxInboundMessageSize()).contains(8_388_608);
    }

    @Test
    void maxInboundMessageSizeBelowOneBoundedRecordWithMarginIsRejected() {
        assertThatThrownBy(() -> new OpMonitorServerProperties(
                config(Map.of("xroad.op-monitor.rpc.max-inbound-message-size", "65535"))).maxInboundMessageSize())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("65536");
    }

    @Test
    void maxInboundMessageSizeOf64KibIsAccepted() {
        var properties = new OpMonitorServerProperties(
                config(Map.of("xroad.op-monitor.rpc.max-inbound-message-size", "65536")));

        assertThat(properties.maxInboundMessageSize()).contains(65_536);
    }

    @Test
    void grpcServerIsEnabledWithoutAnyRow() {
        var properties = new OpMonitorServerProperties(config(Map.of()));

        assertThat(properties.enabled()).isTrue();
    }

    @Test
    void allowedClientNamesAreEmptyWhenNotConfigured() {
        var properties = new OpMonitorServerProperties(config(Map.of()));

        assertThat(properties.allowedClientNames()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   \t "})
    void blankAllowedClientNamesMeanCaOnly(String value) {
        var properties = new OpMonitorServerProperties(config(Map.of(ALLOWED_CLIENT_NAMES, value)));

        assertThat(properties.allowedClientNames()).isEmpty();
    }

    @Test
    void allowedClientNamesAreSplitOnCommasAndTrimmed() {
        var properties = new OpMonitorServerProperties(config(Map.of(ALLOWED_CLIENT_NAMES, "proxy, ui")));

        assertThat(properties.allowedClientNames()).containsExactly("proxy", "ui");
    }

    @Test
    void ipShapedAllowedClientNameIsValid() {
        var properties = new OpMonitorServerProperties(config(Map.of(ALLOWED_CLIENT_NAMES, "127.0.0.1")));

        assertThat(properties.allowedClientNames()).containsExactly("127.0.0.1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "*.example.org", "proxy,*", "proxy,,ui", "proxy,", ",", ",,", ", ,"})
    void malformedAllowedClientNamesAreRejectedNamingTheSetting(String value) {
        var overrides = Map.of(ALLOWED_CLIENT_NAMES, value);

        assertThatThrownBy(() -> config(overrides))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ALLOWED_CLIENT_NAMES);
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "*.example.org", "proxy,*"})
    void wildcardEntryIsNamedInTheError(String value) {
        var offending = value.substring(value.lastIndexOf(',') + 1);
        var overrides = Map.of(ALLOWED_CLIENT_NAMES, value);

        assertThatThrownBy(() -> config(overrides))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(offending);
    }

    @Test
    void emptyEntryIsLocatedInTheError() {
        var overrides = Map.of(ALLOWED_CLIENT_NAMES, "proxy,,ui");

        assertThatThrownBy(() -> config(overrides))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("proxy,,ui")
                .hasMessageContaining("entry 2");
    }

    @Test
    void malformedAllowedClientNamesAreRejectedWhenRpcTlsIsOff() {
        var builder = XRoadConfigBuilder.create()
                .register(OpMonitorConfigKeys.instance())
                .register(CommonRpcConfigKeys.instance())
                .overrides(Map.of(ALLOWED_CLIENT_NAMES, "proxy,,ui", "xroad.common-rpc.use-tls", "false"));

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ALLOWED_CLIENT_NAMES);
    }

    private static XRoadConfig config(Map<String, String> overrides) {
        return XRoadConfigBuilder.create().register(OpMonitorConfigKeys.instance()).overrides(overrides).build();
    }
}
