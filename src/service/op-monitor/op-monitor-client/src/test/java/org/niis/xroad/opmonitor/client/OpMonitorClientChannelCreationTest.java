/*
 * The MIT License
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
package org.niis.xroad.opmonitor.client;

import io.grpc.InsecureChannelCredentials;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.CommonRpcConfigKeys;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.common.rpc.credentials.ChannelTrust;
import org.niis.xroad.common.rpc.credentials.RpcCredentialsConfigurer;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpMonitorClientChannelCreationTest {
    private final RpcCredentialsConfigurer credentialsConfigurer = mock(RpcCredentialsConfigurer.class);
    private final OpMonitorChannelTrustResolver resolver = mock(OpMonitorChannelTrustResolver.class);
    private OpMonitorClient client;

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void credentialBuildFailureSurfacesAsTrustFailureWithTheCauseKept() {
        var buildFailure = XrdRuntimeException.systemInternalError("credentials could not be built");
        when(resolver.resolve()).thenReturn(ChannelTrust.shared());
        when(credentialsConfigurer.createClientCredentials(any(ChannelTrust.class))).thenThrow(buildFailure);
        client = newClient("3000");

        assertThatThrownBy(client::init)
                .isInstanceOfSatisfying(XrdRuntimeException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.OP_MONITOR_TRUST_FAILURE.code());
                    assertThat(e.getDetails()).isEqualTo("The op-monitor channel trust could not be established");
                    assertThat(e.getCause()).isSameAs(buildFailure);
                    assertThat(e.getMessage()).doesNotContain("credentials could not be built");
                });
        assertThat(client.getChannel()).isNull();
    }

    @Test
    void invalidDeadlineIsNotReportedAsTrustFailure() {
        when(resolver.resolve()).thenReturn(ChannelTrust.shared());
        client = newClient("0");

        assertThatThrownBy(client::init).isInstanceOf(IllegalArgumentException.class);
        verify(credentialsConfigurer, never()).createClientCredentials(any(ChannelTrust.class));
        assertThat(client.getChannel()).isNull();
    }

    @Test
    void failedCreationIsNotRememberedAndTheNextCallTriesAgain() {
        var trust = ChannelTrust.shared();
        when(resolver.resolve()).thenReturn(trust);
        when(credentialsConfigurer.createClientCredentials(trust))
                .thenThrow(XrdRuntimeException.systemInternalError("credentials could not be built"))
                .thenReturn(InsecureChannelCredentials.create());
        client = newClient("3000");

        assertThatThrownBy(client::init).isInstanceOf(XrdRuntimeException.class);
        assertThat(client.getChannel()).isNull();

        client.init();

        assertThat(client.getChannel()).isNotNull();
    }

    private OpMonitorClient newClient(String deadlineAfter) {
        return new OpMonitorClient(new RpcChannelFactory(credentialsConfigurer),
                new OpMonitorRpcChannelProperties(config(deadlineAfter)), resolver);
    }

    private static XRoadConfig config(String deadlineAfter) {
        return XRoadConfigBuilder.create()
                .register(CommonRpcConfigKeys.instance())
                .overrides(Map.of(
                        "xroad.common-rpc.channel.op-monitor.host", "localhost",
                        "xroad.common-rpc.channel.op-monitor.port", "1",
                        "xroad.common-rpc.channel.op-monitor.deadline-after", deadlineAfter))
                .build();
    }
}
