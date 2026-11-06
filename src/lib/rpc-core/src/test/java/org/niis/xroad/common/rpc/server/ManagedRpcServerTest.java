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
package org.niis.xroad.common.rpc.server;

import io.grpc.BindableService;
import io.grpc.InsecureServerCredentials;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.rpc.RpcServerProperties;
import org.niis.xroad.common.rpc.credentials.RpcCredentialsConfigurer;

import java.io.IOException;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManagedRpcServerTest {
    private final RpcCredentialsConfigurer credentialsConfigurer = mock(RpcCredentialsConfigurer.class);
    private final RpcServerProperties serverProperties = mock(RpcServerProperties.class);
    private ManagedRpcServer server;

    @AfterEach
    void tearDown() throws InterruptedException {
        if (server != null) {
            server.destroy();
        }
    }

    @Test
    void allowedClientNamesAreGivenToTheServerCredentials() throws IOException {
        givenEnabledServer();
        server = new ManagedRpcServer(List.<BindableService>of(), serverProperties, credentialsConfigurer,
                List.of("proxy", "ui")) {
        };

        server.init();

        verify(credentialsConfigurer).createServerCredentials(List.of("proxy", "ui"));
    }

    @Test
    void constructorWithoutClientNamesGivesAnEmptyListToTheServerCredentials() throws IOException {
        givenEnabledServer();
        server = new ManagedRpcServer(List.<BindableService>of(), serverProperties, credentialsConfigurer) {
        };

        server.init();

        verify(credentialsConfigurer).createServerCredentials(List.of());
    }

    private void givenEnabledServer() {
        when(serverProperties.enabled()).thenReturn(true);
        when(serverProperties.listenAddress()).thenReturn("127.0.0.1");
        when(serverProperties.port()).thenReturn(0);
        when(credentialsConfigurer.createServerCredentials(anyCollection())).thenReturn(InsecureServerCredentials.create());
    }
}
