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
import org.niis.xroad.globalconf.model.SharedParameters;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.util.RestRequestContext;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.MAINTENANCE_MODE;
import static org.niis.xroad.common.core.exception.ErrorCode.UNKNOWN_MEMBER;

class DefaultServiceAddressResolverTest {

    private GlobalConfProvider globalConfProvider;
    private ProxyProperties proxyProperties;
    private DefaultServiceAddressResolver resolver;

    private ServiceId serviceProvider;
    private static final String INSTANCE = "DEV";
    private static final String HOST = "192.168.1.10";
    private static final int PORT = 5500;

    @BeforeEach
    void setUp() {
        globalConfProvider = mock(GlobalConfProvider.class);
        proxyProperties = mock(ProxyProperties.class);
        resolver = new DefaultServiceAddressResolver(globalConfProvider, proxyProperties);

        when(proxyProperties.sslEnabled()).thenReturn(false);
        when(proxyProperties.serverProxyPort()).thenReturn(PORT);

        serviceProvider = ServiceId.Conf.create(INSTANCE, "COM", "1234", "TestService", "testService");
        when(globalConfProvider.getMaintenanceMode(any(String.class), any(String.class)))
                .thenReturn(Optional.empty());
    }

    @Test
    void resolveWithNonNullTargetAddressReturnsThatAddressWithoutCallingGlobalConf() {
        var ctx = new RestRequestContext(mock(ee.ria.xroad.common.util.RequestWrapper.class),
                mock(ee.ria.xroad.common.util.ResponseWrapper.class),
                mock(OpMonitoringData.class),
                "http://override-address:5500/");

        var result = resolver.resolve(serviceProvider, null, ctx);

        assertThat(result).containsExactly(URI.create("http://override-address:5500/"));
        verify(globalConfProvider, never()).getProviderAddress(any());
    }

    @Test
    void resolveWithNullTargetAddressCallsGlobalConfAndReturnsURIs() {
        var clientId = serviceProvider.getClientId();
        when(globalConfProvider.getProviderAddress(clientId)).thenReturn(Set.of(HOST));

        var ctx = new RestRequestContext(mock(ee.ria.xroad.common.util.RequestWrapper.class),
                mock(ee.ria.xroad.common.util.ResponseWrapper.class),
                mock(OpMonitoringData.class),
                null);

        var result = resolver.resolve(serviceProvider, null, ctx);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getHost()).isEqualTo(HOST);
        assertThat(result.get(0).getPort()).isEqualTo(PORT);
        verify(globalConfProvider).getProviderAddress(clientId);
    }

    @Test
    void resolveFiltersAddressesInMaintenanceMode() {
        var clientId = serviceProvider.getClientId();
        when(globalConfProvider.getProviderAddress(clientId)).thenReturn(Set.of(HOST));
        var maintenanceMode = new SharedParameters.MaintenanceMode(true, "Under maintenance");
        when(globalConfProvider.getMaintenanceMode(INSTANCE, HOST)).thenReturn(Optional.of(maintenanceMode));

        var ctx = new RestRequestContext(mock(ee.ria.xroad.common.util.RequestWrapper.class),
                mock(ee.ria.xroad.common.util.ResponseWrapper.class),
                null,
                null);

        assertThatThrownBy(() -> resolver.resolve(serviceProvider, null, ctx))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(e -> assertThat(((XrdRuntimeException) e).getCode()).isEqualTo(MAINTENANCE_MODE.code()));
    }

    @Test
    void resolveThrowsMaintenanceModeWhenAllAddressesAreInMaintenanceMode() {
        var clientId = serviceProvider.getClientId();
        when(globalConfProvider.getProviderAddress(clientId)).thenReturn(Set.of(HOST, "192.168.1.11"));
        var maintenanceMode = new SharedParameters.MaintenanceMode(true, "Scheduled maintenance");
        when(globalConfProvider.getMaintenanceMode(any(String.class), any(String.class)))
                .thenReturn(Optional.of(maintenanceMode));

        var ctx = new RestRequestContext(mock(ee.ria.xroad.common.util.RequestWrapper.class),
                mock(ee.ria.xroad.common.util.ResponseWrapper.class),
                null,
                null);

        assertThatThrownBy(() -> resolver.resolve(serviceProvider, null, ctx))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(e -> assertThat(((XrdRuntimeException) e).getCode()).isEqualTo(MAINTENANCE_MODE.code()));
    }

    @Test
    void resolveThrowsUnknownMemberWhenNoAddressesFound() {
        when(globalConfProvider.getProviderAddress(any())).thenReturn(List.of());

        var ctx = new RestRequestContext(mock(ee.ria.xroad.common.util.RequestWrapper.class),
                mock(ee.ria.xroad.common.util.ResponseWrapper.class),
                null,
                null);

        assertThatThrownBy(() -> resolver.resolve(serviceProvider, null, ctx))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(e -> assertThat(((XrdRuntimeException) e).getCode()).isEqualTo(UNKNOWN_MEMBER.code()));
    }

    @Test
    void resolveWithSecurityServerIdFiltersToThatServerAddress() {
        var clientId = serviceProvider.getClientId();
        var securityServerId = SecurityServerId.Conf.create(INSTANCE, "COM", "1234", "ss1");
        when(globalConfProvider.getProviderAddress(clientId)).thenReturn(Set.of(HOST, "192.168.1.11"));
        when(globalConfProvider.getSecurityServerAddress(securityServerId)).thenReturn(HOST);
        when(globalConfProvider.getMaintenanceMode(securityServerId)).thenReturn(Optional.empty());

        var ctx = new RestRequestContext(mock(ee.ria.xroad.common.util.RequestWrapper.class),
                mock(ee.ria.xroad.common.util.ResponseWrapper.class),
                null,
                null);

        var result = resolver.resolve(serviceProvider, securityServerId, ctx);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getHost()).isEqualTo(HOST);
    }
}
