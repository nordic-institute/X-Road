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

package org.niis.xroad.proxy.core.configuration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.inject.Named;
import org.apache.commons.io.IOUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.opmonitor.api.OpMonitoringBuffer;
import org.niis.xroad.proxy.core.serverproxy.ClientProxyVersionVerifier;
import org.niis.xroad.proxy.core.serverproxy.HttpClientCreator;
import org.niis.xroad.proxy.core.serverproxy.IdleConnectionMonitorThread;
import org.niis.xroad.proxy.core.serverproxy.ServerProxyHandler;
import org.niis.xroad.proxy.core.util.MessageProcessorFactory;
import org.niis.xroad.serverconf.ServerConfProvider;

public class ServerProxyConfig {

    public static final String SERVER_PROXY_HTTP_CLIENT = "serverProxyHttpClient";

    private static final int IDLE_MONITOR_TIMEOUT = 50;
    private static final int IDLE_MONITOR_INTERVAL = 100;

    @ApplicationScoped
    static class ServerProxyHttpClientInitializer {

        @ApplicationScoped
        @Named(SERVER_PROXY_HTTP_CLIENT)
        CloseableHttpClient serverProxyHttpClient(HttpClientCreator httpClientCreator) throws HttpClientCreator.HttpClientCreatorException {
            return httpClientCreator.getHttpClient();
        }

        public void dispose(@Disposes @Named(SERVER_PROXY_HTTP_CLIENT) CloseableHttpClient serverProxyHttpClient) {
            IOUtils.closeQuietly(serverProxyHttpClient);
        }
    }

    @ApplicationScoped
    ServerProxyHandler serverProxyHandler(MessageProcessorFactory messageProcessorFactory, ProxyProperties proxyProperties,
                                          GlobalConfProvider globalConfProvider, OpMonitoringBuffer opMonitoringBuffer) {
        return new ServerProxyHandler(
                messageProcessorFactory, proxyProperties.server(),
                new ClientProxyVersionVerifier(proxyProperties.server().minSupportedClientVersion().orElse(null)),
                globalConfProvider, opMonitoringBuffer);
    }

    @ApplicationScoped
    HttpClientCreator httpClientCreator(ServerConfProvider serverConfProvider, ProxyProperties proxyProperties) {
        return new HttpClientCreator(serverConfProvider,
                proxyProperties.clientProxy().clientTlsProtocols(), proxyProperties.clientProxy().clientTlsCiphers());
    }

    @ApplicationScoped
    IdleConnectionMonitorThread idleConnectionMonitorThread(HttpClientCreator httpClientCreator)
            throws HttpClientCreator.HttpClientCreatorException {
        IdleConnectionMonitorThread connMonitor = new IdleConnectionMonitorThread(httpClientCreator.getConnectionManager());
        connMonitor.setIntervalMilliseconds(IDLE_MONITOR_INTERVAL);
        connMonitor.setConnectionIdleTimeMilliseconds(IDLE_MONITOR_TIMEOUT);
        return connMonitor;
    }
}
