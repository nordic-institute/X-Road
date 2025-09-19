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

import io.quarkus.arc.lookup.LookupIfProperty;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.inject.Named;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
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
import org.niis.xroad.proxy.core.addon.messagelog.clientproxy.AsicContainerHandler;
import org.niis.xroad.proxy.core.addon.metaservice.clientproxy.MetadataHandler;
import org.niis.xroad.proxy.core.clientproxy.AbstractClientProxyHandler;
import org.niis.xroad.proxy.core.clientproxy.AuthTrustVerifier;
import org.niis.xroad.proxy.core.clientproxy.ClientRestMessageHandler;
import org.niis.xroad.proxy.core.clientproxy.ClientSoapMessageHandler;
import org.niis.xroad.proxy.core.clientproxy.FastestConnectionSelectingSSLSocketFactory;
import org.niis.xroad.proxy.core.clientproxy.ReloadingSSLSocketFactory;
import org.niis.xroad.proxy.core.serverproxy.IdleConnectionMonitorThread;
import org.niis.xroad.proxy.core.util.CommonBeanProxy;

@Slf4j
public class ProxyClientConfig {

    @ApplicationScoped
    @Priority(1)
    // must be the last handler
    AbstractClientProxyHandler clientSoapMessageHandler(CommonBeanProxy commonBeanProxy, @Named("proxyHttpClient") HttpClient client) {
        return new ClientSoapMessageHandler(commonBeanProxy, client);
    }

    @ApplicationScoped
    @Priority(100)
    @LookupIfProperty(name = "xroad.proxy.addon.meta-services.enabled", stringValue = "true")
    AbstractClientProxyHandler metadataHandler(CommonBeanProxy commonBeanProxy, @Named("proxyHttpClient") HttpClient client) {
        return new MetadataHandler(commonBeanProxy, client);
    }

    @ApplicationScoped
    @Priority(200)
    @LookupIfProperty(name = "xroad.proxy.addon.message-log.enabled", stringValue = "true")
    AbstractClientProxyHandler asicContainerHandler(CommonBeanProxy commonBeanProxy, @Named("proxyHttpClient") HttpClient client) {
        return new AsicContainerHandler(commonBeanProxy, client);
    }

    @ApplicationScoped
    @Priority(1000)
    AbstractClientProxyHandler clientRestMessageHandler(CommonBeanProxy commonBeanProxy, @Named("proxyHttpClient") HttpClient client) {
        return new ClientRestMessageHandler(commonBeanProxy, client);
    }

    @ApplicationScoped
    public static class ProxyHttpClientInitializer {

        private IdleConnectionMonitorThread connectionMonitor;

        @ApplicationScoped
        @Named("proxyHttpClient")
        public CloseableHttpClient proxyHttpClient(ProxyProperties proxyProperties,
                                                   AuthTrustVerifier authTrustVerifier,
                                                   ReloadingSSLSocketFactory reloadingSSLSocketFactory) {
            log.trace("createClient()");

            int timeout = proxyProperties.clientProxy().clientProxyTimeout();
            int socketTimeout = proxyProperties.clientProxy().clientHttpclientTimeout();
            RequestConfig.Builder rb = RequestConfig.custom();
            rb.setConnectTimeout(timeout);
            rb.setConnectionRequestTimeout(timeout);
            rb.setSocketTimeout(socketTimeout);

            HttpClientBuilder cb = HttpClients.custom();
            HttpClientConnectionManager connectionManager = getClientConnectionManager(proxyProperties,
                    authTrustVerifier, reloadingSSLSocketFactory);
            cb.setConnectionManager(connectionManager);

            if (proxyProperties.clientProxy().clientUseIdleConnectionMonitor()) {
                connectionMonitor = new IdleConnectionMonitorThread(connectionManager);
                connectionMonitor.setIntervalMilliseconds(proxyProperties.clientProxy().clientIdleConnectionMonitorInterval());
                connectionMonitor.setConnectionIdleTimeMilliseconds(
                        proxyProperties.clientProxy().clientIdleConnectionMonitorTimeout());
                connectionMonitor.start();
            }

            cb.setDefaultRequestConfig(rb.build());

            // Disable request retry
            cb.setRetryHandler(new DefaultHttpRequestRetryHandler(0, false));

            return cb.build();
        }

        public void dispose(@Disposes @Named("proxyHttpClient") CloseableHttpClient httpClient) {
            if (connectionMonitor != null) {
                connectionMonitor.shutdown();
            }
            IOUtils.closeQuietly(httpClient);
        }

        private HttpClientConnectionManager getClientConnectionManager(ProxyProperties proxyProperties,
                                                                       AuthTrustVerifier authTrustVerifier,
                                                                       ReloadingSSLSocketFactory reloadingSSLSocketFactory) {
            RegistryBuilder<ConnectionSocketFactory> sfr = RegistryBuilder.create();

            sfr.register("http", PlainConnectionSocketFactory.INSTANCE);

            if (proxyProperties.sslEnabled()) {
                sfr.register("https", createSSLSocketFactory(authTrustVerifier, reloadingSSLSocketFactory,
                        proxyProperties.clientProxy()));
            }

            SocketConfig.Builder sockBuilder = SocketConfig.custom().setTcpNoDelay(true);
            sockBuilder.setSoLinger(proxyProperties.clientProxy().clientHttpclientSoLinger());
            sockBuilder.setSoTimeout(proxyProperties.clientProxy().clientHttpclientTimeout());
            SocketConfig socketConfig = sockBuilder.build();

            PoolingHttpClientConnectionManager poolingManager = new PoolingHttpClientConnectionManager(sfr.build());
            poolingManager.setMaxTotal(proxyProperties.clientProxy().poolTotalMaxConnections());
            poolingManager.setDefaultMaxPerRoute(proxyProperties.clientProxy().poolTotalDefaultMaxConnectionsPerRoute());
            poolingManager.setDefaultSocketConfig(socketConfig);
            poolingManager.setValidateAfterInactivity(
                    proxyProperties.clientProxy().poolValidateConnectionsAfterInactivityOfMillis());

            return poolingManager;
        }

        private SSLConnectionSocketFactory createSSLSocketFactory(AuthTrustVerifier authTrustVerifier,
                                                                  ReloadingSSLSocketFactory reloadingSSLSocketFactory,
                                                                  ProxyProperties.ClientProxyProperties clientProxyProperties) {
            return new FastestConnectionSelectingSSLSocketFactory(authTrustVerifier, reloadingSSLSocketFactory,
                    clientProxyProperties);
        }
    }

}
