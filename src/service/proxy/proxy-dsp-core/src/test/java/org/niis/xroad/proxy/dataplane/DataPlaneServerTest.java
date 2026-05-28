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
package org.niis.xroad.proxy.dataplane;

import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataPlaneServerTest {

    @Mock
    private DataPlaneServerProperties properties;
    @Mock
    private XRoadDataPlaneSignalingApiController signalingController;

    private DataPlaneServer dataPlaneServer;

    @AfterEach
    void tearDown() throws Exception {
        if (dataPlaneServer != null) {
            dataPlaneServer.stop();
        }
    }

    private void stubProperties() {
        when(properties.listenPort()).thenReturn(0);
        when(properties.listenAddress()).thenReturn("127.0.0.1");
        when(properties.threadPoolMin()).thenReturn(1);
        when(properties.threadPoolMax()).thenReturn(5);
        when(properties.threadPoolIdleTimeout()).thenReturn(1000);
    }

    @Test
    void startBindsToConfiguredPortAndMountsSignalingController() throws Exception {
        stubProperties();
        dataPlaneServer = new DataPlaneServer(properties, signalingController);
        dataPlaneServer.start();

        assertThat(dataPlaneServer.getServer().isStarted()).isTrue();

        var connector = (ServerConnector) dataPlaneServer.getServer().getConnectors()[0];
        int port = connector.getLocalPort();
        assertThat(port).isPositive();

        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/full/api/v1/dataflows/probe"))
                .GET()
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.discarding());
        assertThat(response.statusCode()).isBetween(200, 499);
    }

    @Test
    void stopShutsDownServer() throws Exception {
        stubProperties();
        dataPlaneServer = new DataPlaneServer(properties, signalingController);
        dataPlaneServer.start();
        dataPlaneServer.stop();

        assertThat(dataPlaneServer.getServer().isStopped()).isTrue();
    }

    @Test
    void stopBeforeStartIsNoop() {
        dataPlaneServer = new DataPlaneServer(properties, signalingController);
        org.assertj.core.api.Assertions.assertThatNoException().isThrownBy(() -> dataPlaneServer.stop());
    }
}
