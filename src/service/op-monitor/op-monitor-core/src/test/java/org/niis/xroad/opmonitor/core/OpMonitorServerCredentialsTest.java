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
package org.niis.xroad.opmonitor.core;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.grpc.ServerCredentials;
import io.grpc.util.AdvancedTlsX509KeyManager;
import io.grpc.util.AdvancedTlsX509TrustManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.OpMonitorConfigKeys;
import org.niis.xroad.common.rpc.RpcProperties;
import org.niis.xroad.common.rpc.VaultKeyProvider;
import org.niis.xroad.common.rpc.credentials.ClientIdentityMatcher;
import org.niis.xroad.common.rpc.credentials.RpcCredentialsConfigurer;
import org.niis.xroad.common.rpc.test.TestCa;
import org.niis.xroad.opmonitor.core.config.OpMonitorServerProperties;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OpMonitorServerCredentialsTest {
    private static final String ALLOWED_CLIENT_NAMES = "xroad.op-monitor.rpc.allowed-client-names";
    private static final String USE_TLS = "xroad.common-rpc.use-tls";

    private final ServerCredentials sharedServerCredentials = mock(ServerCredentials.class);
    private final RpcCredentialsConfigurer sharedCredentials = mock(RpcCredentialsConfigurer.class);
    private final VaultKeyProvider vaultKeyProvider = mock(VaultKeyProvider.class);
    @SuppressWarnings("unchecked")
    private final Supplier<VaultKeyProvider> vaultKeyProviderSupplier = mock(Supplier.class);
    private final ListAppender<ILoggingEvent> log = new ListAppender<>();
    private Level loggerLevel;

    @BeforeEach
    void setUp() {
        when(sharedCredentials.createServerCredentials()).thenReturn(sharedServerCredentials);
        when(vaultKeyProviderSupplier.get()).thenReturn(vaultKeyProvider);
        loggerLevel = logger().getLevel();
        logger().setLevel(Level.WARN);
        log.start();
        logger().addAppender(log);
    }

    @AfterEach
    void tearDown() {
        logger().detachAppender(log);
        logger().setLevel(loggerLevel);
    }

    @Test
    void emptyListUsesTheSharedCredentialsObject() {
        var credentials = credentials("", true).create();

        assertThat(credentials).isSameAs(sharedServerCredentials);
        verifyNoInteractions(vaultKeyProvider);
        assertThat(log.list).isEmpty();
    }

    @Test
    void listWithTlsOffUsesTheSharedCredentialsObjectAndLogsOneWarningNamingBothSettings() {
        var credentials = credentials("proxy,ui", false).create();

        assertThat(credentials).isSameAs(sharedServerCredentials);
        verifyNoInteractions(vaultKeyProvider);
        assertThat(log.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains(ALLOWED_CLIENT_NAMES).contains(USE_TLS);
        });
    }

    @Test
    void listWithTlsOnBuildsDedicatedCredentialsFilteringOnTheListedNames() throws Exception {
        when(vaultKeyProvider.getKeyManager()).thenReturn(new AdvancedTlsX509KeyManager());
        when(vaultKeyProvider.createVerifyingTrustManager(any()))
                .thenReturn(AdvancedTlsX509TrustManager.newBuilder().build());

        var credentials = credentials("proxy, UI", true).create();

        assertThat(credentials).isNotSameAs(sharedServerCredentials);
        verify(sharedCredentials, never()).createServerCredentials();
        verify(vaultKeyProvider, never()).getTrustManager();
        var verifier = ArgumentCaptor.forClass(AdvancedTlsX509TrustManager.SslSocketAndEnginePeerVerifier.class);
        verify(vaultKeyProvider).createVerifyingTrustManager(verifier.capture());
        var matcher = (ClientIdentityMatcher) verifier.getValue();
        var ca = TestCa.selfSigned("Security Server CA");
        assertThat(matcher.matches(ca.issueLeaf("proxy", List.of(), List.of()).chain())).isTrue();
        assertThat(matcher.matches(ca.issueLeaf("x", List.of("ui"), List.of()).chain())).isTrue();
        assertThat(matcher.matches(ca.issueLeaf("signer", List.of(), List.of()).chain())).isFalse();
    }

    private OpMonitorServerCredentials credentials(String allowedClientNames, boolean useTls) {
        var rpcProperties = mock(RpcProperties.class);
        when(rpcProperties.useTls()).thenReturn(useTls);
        return new OpMonitorServerCredentials(new OpMonitorServerProperties(config(allowedClientNames)), rpcProperties,
                sharedCredentials, vaultKeyProviderSupplier);
    }

    private static XRoadConfig config(String allowedClientNames) {
        return XRoadConfigBuilder.create().register(OpMonitorConfigKeys.instance())
                .overrides(Map.of(ALLOWED_CLIENT_NAMES, allowedClientNames)).build();
    }

    private static Logger logger() {
        return (Logger) LoggerFactory.getLogger(OpMonitorServerCredentials.class);
    }
}
