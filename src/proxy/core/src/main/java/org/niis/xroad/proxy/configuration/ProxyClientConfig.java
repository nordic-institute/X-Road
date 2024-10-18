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
import ee.ria.xroad.proxy.clientproxy.AuthTrustVerifier;
import ee.ria.xroad.proxy.clientproxy.ClientProxy;
import ee.ria.xroad.proxy.clientproxy.ClientRestMessageHandler;
import ee.ria.xroad.proxy.clientproxy.ClientSoapMessageHandler;
import ee.ria.xroad.proxy.clientproxy.FastestConnectionSelectingSSLSocketFactory;
import ee.ria.xroad.proxy.conf.KeyConfProvider;
import ee.ria.xroad.proxy.serverproxy.IdleConnectionMonitorThread;
import ee.ria.xroad.proxy.util.SSLContextUtil;

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
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.niis.xroad.proxy.ProxyProperties;
import org.niis.xroad.proxy.edc.AssetAuthorizationManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class ProxyClientConfig {

    @Bean
    ClientProxy clientProxy(ProxyProperties proxyProperties,
                            @Qualifier("proxyHttpClient") HttpClient httpClient,
                            ClientRestMessageHandler clientRestMessageHandler,
                            ClientSoapMessageHandler clientSoapMessageHandler,
                            GlobalConfProvider globalConfProvider,
                            KeyConfProvider keyConfProvider,
                            ServerConfProvider serverConfProvider,
                            CertChainFactory certChainFactory,
                            AuthTrustVerifier authTrustVerifier) throws Exception {
        return new ClientProxy(proxyProperties.getClientProxy(), httpClient, clientRestMessageHandler, clientSoapMessageHandler,
                globalConfProvider, keyConfProvider, serverConfProvider, certChainFactory, authTrustVerifier);
    }

    @Bean
    ClientRestMessageHandler clientRestMessageHandler(GlobalConfProvider globalConfProvider,
                                                      KeyConfProvider keyConfProvider,
                                                      ServerConfProvider serverConfProvider,
                                                      CertChainFactory certChainFactory,
                                                      @Qualifier("proxyHttpClient") HttpClient httpClient,
                                                      @Autowired(required = false) AssetAuthorizationManager assetAuthorizationManager) {
        return new ClientRestMessageHandler(globalConfProvider, keyConfProvider, serverConfProvider, certChainFactory,
                httpClient, assetAuthorizationManager);
    }

    @Bean
    ClientSoapMessageHandler clientSoapMessageHandler(GlobalConfProvider globalConfProvider,
                                                      KeyConfProvider keyConfProvider,
                                                      ServerConfProvider serverConfProvider,
                                                      CertChainFactory certChainFactory,
                                                      @Qualifier("proxyHttpClient") HttpClient httpClient,
                                                      @Autowired(required = false) AssetAuthorizationManager assetAuthorizationManager) {
        return new ClientSoapMessageHandler(globalConfProvider, keyConfProvider, serverConfProvider, certChainFactory,
                httpClient, assetAuthorizationManager);
    }

    @ConditionalOnProperty(name = "xroad.proxy.client-proxy.client-use-idle-connection-monitor",havingValue = "true")
    @Bean
    IdleConnectionMonitorThread idleConnectionMonitorThread(ProxyProperties proxyProperties,
            @Qualifier("proxyHttpClientManager") HttpClientConnectionManager connectionManager) {
        var connectionMonitor = new IdleConnectionMonitorThread(connectionManager);
        connectionMonitor.setIntervalMilliseconds(proxyProperties.getClientProxy().clientIdleConnectionMonitorInterval());
        connectionMonitor.setConnectionIdleTimeMilliseconds(
                proxyProperties.getClientProxy().clientIdleConnectionMonitorTimeout());
        return connectionMonitor;
    }

    @Bean("proxyHttpClient")
    CloseableHttpClient proxyHttpClient(ProxyProperties proxyProperties,
            @Qualifier("proxyHttpClientManager") HttpClientConnectionManager connectionManager) {
        log.trace("createClient()");

        int timeout = proxyProperties.getClientTimeout();
        int socketTimeout = proxyProperties.getClientProxy().clientHttpclientTimeout();
        RequestConfig.Builder rb = RequestConfig.custom();
        rb.setConnectTimeout(timeout);
        rb.setConnectionRequestTimeout(timeout);
        rb.setSocketTimeout(socketTimeout);

        HttpClientBuilder cb = HttpClients.custom();

        cb.setConnectionManager(connectionManager);
        cb.setDefaultRequestConfig(rb.build());

        // Disable request retry
        cb.setRetryHandler(new DefaultHttpRequestRetryHandler(0, false));

        return cb.build();
    }

    @Bean("proxyHttpClientManager")
    HttpClientConnectionManager getClientConnectionManager(ProxyProperties proxyProperties, GlobalConfProvider globalConfProvider,
                                                           KeyConfProvider keyConfProvider, AuthTrustVerifier authTrustVerifier)
            throws Exception {
        RegistryBuilder<ConnectionSocketFactory> sfr = RegistryBuilder.create();

        sfr.register("http", PlainConnectionSocketFactory.INSTANCE);

        if (SystemProperties.isSslEnabled()) {
            sfr.register("https", createSSLSocketFactory(globalConfProvider, keyConfProvider, authTrustVerifier));
        }
        ProxyProperties.ClientProxyProperties clientProxyProperties = proxyProperties.getClientProxy();
        SocketConfig.Builder sockBuilder = SocketConfig.custom().setTcpNoDelay(true);
        sockBuilder.setSoLinger(clientProxyProperties.clientHttpclientSoLinger());
        sockBuilder.setSoTimeout(clientProxyProperties.clientHttpclientTimeout());
        SocketConfig socketConfig = sockBuilder.build();

        PoolingHttpClientConnectionManager poolingManager = new PoolingHttpClientConnectionManager(sfr.build());
        poolingManager.setMaxTotal(clientProxyProperties.poolTotalMaxConnections());
        poolingManager.setDefaultMaxPerRoute(clientProxyProperties.poolTotalDefaultMaxConnectionsPerRoute());
        poolingManager.setDefaultSocketConfig(socketConfig);
        poolingManager.setValidateAfterInactivity(
                clientProxyProperties.poolValidateConnectionsAfterInactivityOfMillis());

        return poolingManager;
    }

    private SSLConnectionSocketFactory createSSLSocketFactory(GlobalConfProvider globalConfProvider, KeyConfProvider keyConfProvider,
                                                              AuthTrustVerifier authTrustVerifier)
            throws Exception {
        return new FastestConnectionSelectingSSLSocketFactory(authTrustVerifier, SSLContextUtil.createXroadSSLContext(globalConfProvider,
                keyConfProvider)
        );
    }

}
