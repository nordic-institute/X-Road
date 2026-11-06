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
package org.niis.xroad.opmonitor.core;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.grpc.InsecureServerCredentials;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.OpMonitorConfigKeys;
import org.niis.xroad.common.rpc.RpcProperties;
import org.niis.xroad.common.rpc.credentials.RpcCredentialsConfigurer;
import org.niis.xroad.opmonitor.core.config.OpMonitorServerProperties;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpMonitorRpcServerTest {
    private static final String ALLOWED_CLIENT_NAMES = "xroad.op-monitor.rpc.allowed-client-names";

    private final RpcCredentialsConfigurer credentialsConfigurer = mock(RpcCredentialsConfigurer.class);
    private final RpcProperties rpcProperties = mock(RpcProperties.class);
    private final Logger serverLogger = (Logger) LoggerFactory.getLogger(OpMonitorRpcServer.class);
    private final ListAppender<ILoggingEvent> logEvents = new ListAppender<>();
    private Level originalLevel;
    private OpMonitorRpcServer server;

    @BeforeEach
    void setUp() {
        when(credentialsConfigurer.createServerCredentials(anyCollection())).thenReturn(InsecureServerCredentials.create());
        originalLevel = serverLogger.getLevel();
        serverLogger.setLevel(Level.WARN);
        logEvents.start();
        serverLogger.addAppender(logEvents);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        serverLogger.detachAppender(logEvents);
        serverLogger.setLevel(originalLevel);
        if (server != null) {
            server.destroy();
        }
    }

    @Test
    void configuredClientNamesReachTheServerCredentials() throws IOException {
        when(rpcProperties.useTls()).thenReturn(true);
        server = newServer("proxy, ui");

        server.init();

        verify(credentialsConfigurer).createServerCredentials(List.of("proxy", "ui"));
    }

    @Test
    void noConfiguredClientNamesGiveAnEmptyListToTheServerCredentials() throws IOException {
        when(rpcProperties.useTls()).thenReturn(true);
        server = newServer("");

        server.init();

        verify(credentialsConfigurer).createServerCredentials(List.of());
    }

    @Test
    void clientNamesWithTlsOffLogExactlyOneWarningNamingBothSettings() {
        when(rpcProperties.useTls()).thenReturn(false);

        server = newServer("proxy");

        assertThat(warnings()).singleElement().satisfies(message ->
                assertThat(message).contains(ALLOWED_CLIENT_NAMES, "xroad.common-rpc.use-tls"));
    }

    @Test
    void emptyClientNamesWithTlsOffLogNoWarning() {
        when(rpcProperties.useTls()).thenReturn(false);

        server = newServer("");

        assertThat(warnings()).isEmpty();
    }

    @Test
    void clientNamesWithTlsOnLogNoWarning() {
        when(rpcProperties.useTls()).thenReturn(true);

        server = newServer("proxy");

        assertThat(warnings()).isEmpty();
    }

    private List<String> warnings() {
        return logEvents.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private OpMonitorRpcServer newServer(String allowedClientNames) {
        var config = XRoadConfigBuilder.create()
                .register(OpMonitorConfigKeys.instance())
                .overrides(Map.of(
                        ALLOWED_CLIENT_NAMES, allowedClientNames,
                        "xroad.op-monitor.rpc.port", "0"))
                .build();
        return new OpMonitorRpcServer(List.of(), new OpMonitorServerProperties(config), credentialsConfigurer,
                rpcProperties);
    }
}
