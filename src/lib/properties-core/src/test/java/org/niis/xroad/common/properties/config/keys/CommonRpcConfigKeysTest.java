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
package org.niis.xroad.common.properties.config.keys;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommonRpcConfigKeysTest {
    private static final String MAX_INBOUND_MESSAGE_SIZE_KEY = "xroad.common-rpc.channel.op-monitor.max-inbound-message-size";
    private static final int FOUR_MIB = 4_194_304;
    private static final int SIXTEEN_MIB = 16_777_216;

    @Test
    void opMonitorChannelMessageLimitIsExposedUnderItsKey() {
        assertThat(CommonRpcConfigKeys.CHANNEL_OP_MONITOR_MAX_INBOUND_MESSAGE_SIZE.key()).isEqualTo(MAX_INBOUND_MESSAGE_SIZE_KEY);
        assertThat(CommonRpcConfigKeys.CHANNEL_OP_MONITOR_MAX_INBOUND_MESSAGE_SIZE.exposedInUi()).isTrue();
    }

    @Test
    void opMonitorChannelMessageLimitMinimumIsFourMebibytes() {
        assertThat(CommonRpcConfigKeys.MIN_OP_MONITOR_CHANNEL_MESSAGE_SIZE).isEqualTo(FOUR_MIB);
    }

    @Test
    void opMonitorChannelMessageLimitDefaultIsSixteenMebibytes() {
        assertThat(config(Map.of()).value(CommonRpcConfigKeys.CHANNEL_OP_MONITOR_MAX_INBOUND_MESSAGE_SIZE)).isEqualTo(SIXTEEN_MIB);
    }

    @Test
    void opMonitorChannelMessageLimitDefaultIsNotBelowTheMinimum() {
        assertThat(CommonRpcConfigKeys.CHANNEL_OP_MONITOR_MAX_INBOUND_MESSAGE_SIZE.convertedDefaultValue())
                .isGreaterThanOrEqualTo(CommonRpcConfigKeys.MIN_OP_MONITOR_CHANNEL_MESSAGE_SIZE);
    }

    @Test
    void opMonitorChannelMessageLimitBelowTheMinimumIsRejected() {
        Map<String, String> overrides = Map.of(MAX_INBOUND_MESSAGE_SIZE_KEY, String.valueOf(FOUR_MIB - 1));

        assertThatThrownBy(() -> config(overrides))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(MAX_INBOUND_MESSAGE_SIZE_KEY)
                .hasMessageContaining(String.valueOf(FOUR_MIB));
    }

    @Test
    void opMonitorChannelMessageLimitFromTheMinimumUpToIntegerMaxIsAccepted() {
        for (int limit : new int[] {FOUR_MIB, Integer.MAX_VALUE}) {
            assertThat(config(Map.of(MAX_INBOUND_MESSAGE_SIZE_KEY, String.valueOf(limit)))
                    .value(CommonRpcConfigKeys.CHANNEL_OP_MONITOR_MAX_INBOUND_MESSAGE_SIZE)).isEqualTo(limit);
        }
    }

    @Test
    void identitySourceIsDeclaredUnderItsKeyAndDefaultsToOpenbao() {
        assertThat(CommonRpcConfigKeys.IDENTITY_SOURCE.key()).isEqualTo("xroad.common-rpc.identity-source");
        assertThat(CommonRpcConfigKeys.IDENTITY_SOURCE.defaultValue()).isEqualTo("openbao");
        assertThat(config(Map.of()).value(CommonRpcConfigKeys.IDENTITY_SOURCE)).isEqualTo("openbao");
    }

    @Test
    void identitySourceAcceptsOpenbaoAndFileInAnyCase() {
        for (String source : new String[] {"openbao", "file", "FILE", "OpenBao"}) {
            assertThat(config(Map.of("xroad.common-rpc.identity-source", source))
                    .value(CommonRpcConfigKeys.IDENTITY_SOURCE)).isEqualTo(source);
        }
    }

    @Test
    void identitySourceOutsideTheAllowedValuesIsRejected() {
        var overrides = Map.of("xroad.common-rpc.identity-source", "keystore");

        assertThatThrownBy(() -> config(overrides))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("xroad.common-rpc.identity-source");
    }

    @Test
    void fileIdentityPathsAreDeclaredUnderTheirKeys() {
        assertThat(CommonRpcConfigKeys.FILE_IDENTITY_PRIVATE_KEY_PATH.key())
                .isEqualTo("xroad.common-rpc.file-identity.private-key-path");
        assertThat(CommonRpcConfigKeys.FILE_IDENTITY_CERTIFICATE_PATH.key())
                .isEqualTo("xroad.common-rpc.file-identity.certificate-path");
        assertThat(CommonRpcConfigKeys.FILE_IDENTITY_TRUSTED_CA_PATH.key())
                .isEqualTo("xroad.common-rpc.file-identity.trusted-ca-path");
        assertThat(CommonRpcConfigKeys.instance().keys()).contains(
                CommonRpcConfigKeys.IDENTITY_SOURCE,
                CommonRpcConfigKeys.FILE_IDENTITY_PRIVATE_KEY_PATH,
                CommonRpcConfigKeys.FILE_IDENTITY_CERTIFICATE_PATH,
                CommonRpcConfigKeys.FILE_IDENTITY_TRUSTED_CA_PATH);
    }

    private static XRoadConfig config(Map<String, String> overrides) {
        return XRoadConfigBuilder.create()
                .register(CommonRpcConfigKeys.instance())
                .overrides(overrides)
                .build();
    }
}
