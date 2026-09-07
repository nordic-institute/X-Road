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

import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_SECURITY_SERVER;
import static org.niis.xroad.common.core.exception.ErrorCode.UNKNOWN_MEMBER;

class ProviderSecurityServerResolverTest {

    private static final String INSTANCE = "DEV";
    private static final String HOST_A = "ss-a.example.com";
    private static final String HOST_B = "ss-b.example.com";

    private GlobalConfProvider globalConfProvider;
    private ProviderSecurityServerResolver resolver;
    private ServiceId serviceProvider;

    @BeforeEach
    void setUp() {
        globalConfProvider = mock(GlobalConfProvider.class);
        resolver = new ProviderSecurityServerResolver(globalConfProvider);
        serviceProvider = ServiceId.Conf.create(INSTANCE, "COM", "1234", "TestClient", "testService", "v1");
    }

    @Test
    void resolveWithoutHintReturnsAllProviderAddresses() {
        when(globalConfProvider.getProviderAddress(serviceProvider.getClientId()))
                .thenReturn(Set.of(HOST_A, HOST_B));

        var result = resolver.resolve(serviceProvider, null);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ProviderSecurityServerResolver.ProviderAddress::hostAddress)
                .containsExactlyInAnyOrder(HOST_A, HOST_B);
        assertThat(result).extracting(ProviderSecurityServerResolver.ProviderAddress::serverId)
                .containsOnly((SecurityServerId) null);
    }

    @Test
    void resolveWithValidHintReturnsOnlyThatServer() {
        var hint = SecurityServerId.Conf.create(INSTANCE, "COM", "1234", "ss-a");
        when(globalConfProvider.getProviderAddress(serviceProvider.getClientId()))
                .thenReturn(Set.of(HOST_A, HOST_B));
        when(globalConfProvider.getSecurityServerAddress(hint)).thenReturn(HOST_A);

        var result = resolver.resolve(serviceProvider, hint);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).serverId()).isEqualTo(hint);
        assertThat(result.get(0).hostAddress()).isEqualTo(HOST_A);
    }

    @Test
    void resolveWithUnknownHintThrowsInvalidSecurityServer() {
        var hint = SecurityServerId.Conf.create(INSTANCE, "COM", "1234", "ss-missing");
        when(globalConfProvider.getProviderAddress(serviceProvider.getClientId()))
                .thenReturn(Set.of(HOST_A));
        when(globalConfProvider.getSecurityServerAddress(hint)).thenReturn(null);

        assertThatThrownBy(() -> resolver.resolve(serviceProvider, hint))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex).getCode())
                        .isEqualTo(INVALID_SECURITY_SERVER.code()))
                .hasMessageContaining("Could not find security server");
    }

    @Test
    void resolveWithInvalidHintThrowsInvalidSecurityServer() {
        var hint = SecurityServerId.Conf.create(INSTANCE, "COM", "1234", "ss-elsewhere");
        when(globalConfProvider.getProviderAddress(serviceProvider.getClientId()))
                .thenReturn(Set.of(HOST_A));
        when(globalConfProvider.getSecurityServerAddress(hint)).thenReturn("ss-elsewhere.example.com");

        assertThatThrownBy(() -> resolver.resolve(serviceProvider, hint))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex).getCode())
                        .isEqualTo(INVALID_SECURITY_SERVER.code()))
                .hasMessageContaining("Invalid security server");
    }

    @Test
    void resolveWithNoProviderAddressesThrowsUnknownMember() {
        when(globalConfProvider.getProviderAddress(serviceProvider.getClientId()))
                .thenReturn(Set.of());

        assertThatThrownBy(() -> resolver.resolve(serviceProvider, null))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex).getCode())
                        .isEqualTo(UNKNOWN_MEMBER.code()))
                .hasMessageContaining("Could not find addresses for service provider");
    }
}
