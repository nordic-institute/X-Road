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
package org.niis.xroad.proxy.configuration;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.cert.CertChainFactory;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.proxy.clientproxy.AbstractClientProxyHandler;
import ee.ria.xroad.proxy.clientproxy.AuthTrustVerifier;
import ee.ria.xroad.proxy.clientproxy.ClientProxy;
import ee.ria.xroad.proxy.clientproxy.ClientRestMessageHandler;
import ee.ria.xroad.proxy.clientproxy.ClientSoapMessageHandler;
import ee.ria.xroad.proxy.clientproxy.FastestConnectionSelectingSSLSocketFactory;
import ee.ria.xroad.proxy.conf.KeyConfProvider;
import ee.ria.xroad.proxy.serverproxy.IdleConnectionMonitorThread;
import ee.ria.xroad.proxy.util.SSLContextUtil;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.niis.xroad.proxy.ProxyProperties;
import org.niis.xroad.proxy.edc.AssetAuthorizationManager;

@Slf4j
@ApplicationScoped
public class ProxyClientConfig {

    @Produces
    @ApplicationScoped
    ClientProxy clientProxy(
            ProxyProperties proxyProperties,
            Instance<AbstractClientProxyHandler> clientProxyHandlers,
            ServerConfProvider serverConfProvider) throws Exception {
        return new ClientProxy(
                proxyProperties.clientProxy(),
                clientProxyHandlers.stream().toList(),
                serverConfProvider);
    }

    @Produces
    @ApplicationScoped
    @Priority(100)
        // Highest precedence
    AbstractClientProxyHandler clientRestMessageHandler(
            GlobalConfProvider globalConfProvider,
            KeyConfProvider keyConfProvider,
            ServerConfProvider serverConfProvider,
            CertChainFactory certChainFactory,
            @Named("proxyHttpClient") HttpClient httpClient,
            Instance<AssetAuthorizationManager> assetAuthManager) {
        return new ClientRestMessageHandler(
                globalConfProvider,
                keyConfProvider,
                serverConfProvider,
                certChainFactory,
                httpClient,
                assetAuthManager.isResolvable() ? assetAuthManager.get() : null);
    }

    @Produces
    @ApplicationScoped
    @Priority(1)
        //TODO xroad8 order might not be working.. Verify
        // Lowest precedence
    AbstractClientProxyHandler clientSoapMessageHandler(
            GlobalConfProvider globalConfProvider,
            KeyConfProvider keyConfProvider,
            ServerConfProvider serverConfProvider,
            CertChainFactory certChainFactory,
            @Named("proxyHttpClient") HttpClient httpClient,
            Instance<AssetAuthorizationManager> assetAuthManager) {
        return new ClientSoapMessageHandler(
                globalConfProvider,
                keyConfProvider,
                serverConfProvider,
                certChainFactory,
                httpClient,
                assetAuthManager.isResolvable() ? assetAuthManager.get() : null);
    }

    @Produces
    @ApplicationScoped
    IdleConnectionMonitorThread idleConnectionMonitorThread(
            @ConfigProperty(name = "xroad.proxy.client-proxy.client-use-idle-connection-monitor") boolean enabled,
            ProxyProperties proxyProperties,
            @Named("proxyHttpClientManager") HttpClientConnectionManager connectionManager) {

        if (!enabled) {
            return null;
        }

        var connectionMonitor = new IdleConnectionMonitorThread(connectionManager);
        connectionMonitor.setIntervalMilliseconds(
                proxyProperties.clientProxy().clientIdleConnectionMonitorInterval());
        connectionMonitor.setConnectionIdleTimeMilliseconds(
                proxyProperties.clientProxy().clientIdleConnectionMonitorTimeout());
        return connectionMonitor;
    }

    @Produces
    @ApplicationScoped
    @Named("proxyHttpClient")
    CloseableHttpClient proxyHttpClient(
            ProxyProperties proxyProperties,
            @Named("proxyHttpClientManager") HttpClientConnectionManager connectionManager) {
        log.trace("createClient()");

        var timeout = proxyProperties.clientTimeout();
        var socketTimeout = proxyProperties.clientProxy().clientHttpclientTimeout();

        var requestConfig = RequestConfig.custom()
                .setConnectTimeout(timeout)
                .setConnectionRequestTimeout(timeout)
                .setSocketTimeout(socketTimeout)
                .build();

        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .setRetryHandler(new DefaultHttpRequestRetryHandler(0, false))
                .build();
    }

    @Produces
    @ApplicationScoped
    @Named("proxyHttpClientManager")
    HttpClientConnectionManager getClientConnectionManager(
            ProxyProperties proxyProperties,
            GlobalConfProvider globalConfProvider,
            KeyConfProvider keyConfProvider,
            AuthTrustVerifier authTrustVerifier) throws Exception {

        var sfr = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.INSTANCE);

        if (SystemProperties.isSslEnabled()) {
            sfr.register("https", createSSLSocketFactory(
                    globalConfProvider,
                    keyConfProvider,
                    authTrustVerifier));
        }

        var clientProxyProperties = proxyProperties.clientProxy();
        var socketConfig = SocketConfig.custom()
                .setTcpNoDelay(true)
                .setSoLinger(clientProxyProperties.clientHttpclientSoLinger())
                .setSoTimeout(clientProxyProperties.clientHttpclientTimeout())
                .build();

        var poolingManager = new PoolingHttpClientConnectionManager(sfr.build());
        poolingManager.setMaxTotal(clientProxyProperties.poolTotalMaxConnections());
        poolingManager.setDefaultMaxPerRoute(
                clientProxyProperties.poolTotalDefaultMaxConnectionsPerRoute());
        poolingManager.setDefaultSocketConfig(socketConfig);
        poolingManager.setValidateAfterInactivity(
                clientProxyProperties.poolValidateConnectionsAfterInactivityOfMillis());

        return poolingManager;
    }

    private SSLConnectionSocketFactory createSSLSocketFactory(
            GlobalConfProvider globalConfProvider,
            KeyConfProvider keyConfProvider,
            AuthTrustVerifier authTrustVerifier) throws Exception {
        return new FastestConnectionSelectingSSLSocketFactory(
                authTrustVerifier,
                SSLContextUtil.createXroadSSLContext(globalConfProvider, keyConfProvider));
    }
}
