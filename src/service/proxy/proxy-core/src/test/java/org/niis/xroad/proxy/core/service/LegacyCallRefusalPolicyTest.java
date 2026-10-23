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
package org.niis.xroad.proxy.core.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyCallRefusalPolicyTest {

    private static final String OWN_INSTANCE = "DEV";
    private static final String FEDERATED_INSTANCE = "XRD8";

    private final ProxyProperties proxyProperties = mock(ProxyProperties.class);
    private final GlobalConfProvider globalConfProvider = mock(GlobalConfProvider.class);
    private final LegacyCallRefusalPolicy policy = new LegacyCallRefusalPolicy(proxyProperties, globalConfProvider);

    @BeforeEach
    void setUp() {
        when(globalConfProvider.getInstanceIdentifier()).thenReturn(OWN_INSTANCE);
    }

    @ParameterizedTest(name = "local={0}, own instance={1} -> refused as provider={2}")
    @CsvSource({
            "false, false, false",
            "false, true,  true",
            "true,  false, true",
            "true,  true,  true"
    })
    void providerRefusalFollowsLocalSettingAndOwnInstanceValue(boolean local, boolean ownInstance, boolean expected) {
        when(proxyProperties.legacyProtocolDisabled()).thenReturn(local);
        when(globalConfProvider.isLegacyProtocolDisabled(OWN_INSTANCE)).thenReturn(ownInstance);

        assertThat(policy.refusedAsProvider()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "local={0}, provider instance={1} -> refused as consumer={2}")
    @CsvSource({
            "false, false, false",
            "false, true,  true",
            "true,  false, true",
            "true,  true,  true"
    })
    void consumerRefusalFollowsLocalSettingAndProviderInstanceValue(boolean local, boolean providerInstance,
                                                                    boolean expected) {
        when(proxyProperties.legacyProtocolDisabled()).thenReturn(local);
        when(globalConfProvider.isLegacyProtocolDisabled(FEDERATED_INSTANCE)).thenReturn(providerInstance);

        assertThat(policy.refusedAsConsumer(FEDERATED_INSTANCE)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "local={0}, federated instance value={1}")
    @CsvSource({
            "false, true",
            "true,  true"
    })
    void providerRefusalIgnoresFederatedInstanceValue(boolean local, boolean federatedInstance) {
        when(proxyProperties.legacyProtocolDisabled()).thenReturn(local);
        when(globalConfProvider.isLegacyProtocolDisabled(FEDERATED_INSTANCE)).thenReturn(federatedInstance);

        assertThat(policy.refusedAsProvider()).isEqualTo(local);
    }

    @Test
    void consumerRefusalIgnoresOwnInstanceValue() {
        when(proxyProperties.legacyProtocolDisabled()).thenReturn(false);
        when(globalConfProvider.isLegacyProtocolDisabled(OWN_INSTANCE)).thenReturn(true);

        assertThat(policy.refusedAsConsumer(FEDERATED_INSTANCE)).isFalse();
    }

    @Test
    void absentOrUnreadableInstanceValueDoesNotRefuse() {
        when(proxyProperties.legacyProtocolDisabled()).thenReturn(false);
        when(globalConfProvider.isLegacyProtocolDisabled(any())).thenReturn(false);

        assertThat(policy.refusedAsProvider()).isFalse();
        assertThat(policy.refusedAsConsumer(FEDERATED_INSTANCE)).isFalse();
    }

    @Test
    void instanceValueIsReadOnEveryEvaluation() {
        when(proxyProperties.legacyProtocolDisabled()).thenReturn(false);
        when(globalConfProvider.isLegacyProtocolDisabled(OWN_INSTANCE)).thenReturn(true, false);

        assertThat(policy.refusedAsProvider()).isTrue();
        assertThat(policy.refusedAsProvider()).isFalse();
    }

    @Test
    void localSettingTrueReadsNoGlobalConf() {
        when(proxyProperties.legacyProtocolDisabled()).thenReturn(true);

        assertThat(policy.refusedAsProvider()).isTrue();
        assertThat(policy.refusedAsConsumer(FEDERATED_INSTANCE)).isTrue();

        verify(globalConfProvider, never()).isLegacyProtocolDisabled(any());
    }
}
