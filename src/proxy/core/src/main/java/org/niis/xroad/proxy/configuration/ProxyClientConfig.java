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

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
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

    @Inject
    ProxyProperties proxyProperties;

    @Produces
    @Startup
    @ApplicationScoped
    ClientProxy clientProxy(@Named("proxyHttpClient") HttpClient httpClient,
                            ClientRestMessageHandler clientRestMessageHandler,
                            ClientSoapMessageHandler clientSoapMessageHandler,
                            GlobalConfProvider globalConfProvider,
                            KeyConfProvider keyConfProvider,
                            ServerConfProvider serverConfProvider,
                            CertChainFactory certChainFactory,
                            AuthTrustVerifier authTrustVerifier) throws Exception {
        return new ClientProxy(proxyProperties.getClientProxy(),
                httpClient,
                clientRestMessageHandler,
                clientSoapMessageHandler,
                globalConfProvider,
                keyConfProvider,
                serverConfProvider,
                certChainFactory,
                authTrustVerifier);
    }

    @Produces
    @ApplicationScoped
    ClientRestMessageHandler clientRestMessageHandler(GlobalConfProvider globalConfProvider,
                                                      KeyConfProvider keyConfProvider,
                                                      ServerConfProvider serverConfProvider,
                                                      CertChainFactory certChainFactory,
                                                      @Named("proxyHttpClient") HttpClient httpClient,
                                                      Instance<AssetAuthorizationManager> assetAuthManager) {
        return new ClientRestMessageHandler(globalConfProvider,
                keyConfProvider,
                serverConfProvider,
                certChainFactory,
                httpClient,
                assetAuthManager.isResolvable() ? assetAuthManager.get() : null);
    }

    @Produces
    @ApplicationScoped
    ClientSoapMessageHandler clientSoapMessageHandler(GlobalConfProvider globalConfProvider,
                                                      KeyConfProvider keyConfProvider,
                                                      ServerConfProvider serverConfProvider,
                                                      CertChainFactory certChainFactory,
                                                      @Named("proxyHttpClient") HttpClient httpClient,
                                                      Instance<AssetAuthorizationManager> assetAuthManager) {
        return new ClientSoapMessageHandler(globalConfProvider,
                keyConfProvider,
                serverConfProvider,
                certChainFactory,
                httpClient,
                assetAuthManager.isResolvable() ? assetAuthManager.get() : null);
    }

    @Produces
    @ApplicationScoped
    @ConfigProperty(name = "xroad.proxy.client-proxy.client-use-idle-connection-monitor")
    IdleConnectionMonitorThread idleConnectionMonitorThread(
            @Named("proxyHttpClientManager") HttpClientConnectionManager connectionManager) {
        var connectionMonitor = new IdleConnectionMonitorThread(connectionManager);
        connectionMonitor.setIntervalMilliseconds(
                proxyProperties.getClientProxy().clientIdleConnectionMonitorInterval());
        connectionMonitor.setConnectionIdleTimeMilliseconds(
                proxyProperties.getClientProxy().clientIdleConnectionMonitorTimeout());
        return connectionMonitor;
    }

    @Produces
    @ApplicationScoped
    @Named("proxyHttpClient")
    CloseableHttpClient proxyHttpClient(
            @Named("proxyHttpClientManager") HttpClientConnectionManager connectionManager) {
        log.trace("createClient()");

        int timeout = proxyProperties.getClientTimeout();
        int socketTimeout = proxyProperties.getClientProxy().clientHttpclientTimeout();

        RequestConfig requestConfig = RequestConfig.custom()
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
    HttpClientConnectionManager getClientConnectionManager(GlobalConfProvider globalConfProvider,
                                                           KeyConfProvider keyConfProvider,
                                                           AuthTrustVerifier authTrustVerifier) throws Exception {
        var sfr = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.INSTANCE);

        if (SystemProperties.isSslEnabled()) {
            sfr.register("https", createSSLSocketFactory(globalConfProvider, keyConfProvider, authTrustVerifier));
        }

        var clientProxyProperties = proxyProperties.getClientProxy();
        var socketConfig = SocketConfig.custom()
                .setTcpNoDelay(true)
                .setSoLinger(clientProxyProperties.clientHttpclientSoLinger())
                .setSoTimeout(clientProxyProperties.clientHttpclientTimeout())
                .build();

        var poolingManager = new PoolingHttpClientConnectionManager(sfr.build());
        poolingManager.setMaxTotal(clientProxyProperties.poolTotalMaxConnections());
        poolingManager.setDefaultMaxPerRoute(clientProxyProperties.poolTotalDefaultMaxConnectionsPerRoute());
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
                SSLContextUtil.createXroadSSLContext(globalConfProvider, keyConfProvider)
        );
    }
}
