package org.niis.xroad.proxy.configuration;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.proxy.clientproxy.ClientProxy;
import ee.ria.xroad.proxy.clientproxy.ClientRestMessageHandler;
import ee.ria.xroad.proxy.clientproxy.FastestConnectionSelectingSSLSocketFactory;
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
import org.eclipse.edc.connector.api.management.contractnegotiation.ContractNegotiationApi;
import org.eclipse.edc.connector.api.management.transferprocess.TransferProcessApi;
import org.niis.xroad.edc.management.client.FeignCatalogApi;
import org.niis.xroad.proxy.edc.AuthorizedAssetRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;

@Slf4j
@Configuration
public class ProxyClientConfig {
    private static final int CONNECTOR_SO_LINGER_MILLIS = SystemProperties.getClientProxyConnectorSoLinger() * 1000;

    @Bean(initMethod = "start", destroyMethod = "stop")
    ClientProxy clientProxy(@Qualifier("proxyHttpClient") HttpClient httpClient,
                            ClientRestMessageHandler clientRestMessageHandler) throws Exception {
        return new ClientProxy(httpClient, clientRestMessageHandler);
    }

    @Bean
    ClientRestMessageHandler clientRestMessageHandler(@Qualifier("proxyHttpClient") HttpClient httpClient, AuthorizedAssetRegistry authorizedAssetRegistry,
                                                      FeignCatalogApi catalogApi,
                                                      ContractNegotiationApi contractNegotiationApi,
                                                      TransferProcessApi transferProcessApi) {
        return new ClientRestMessageHandler(httpClient, authorizedAssetRegistry, catalogApi, contractNegotiationApi, transferProcessApi);
    }

    @Conditional(ClientUseIdleConnectionMonitorEnabledCondition.class)
    @Bean(initMethod = "start", destroyMethod = "shutdown")
    IdleConnectionMonitorThread idleConnectionMonitorThread(@Qualifier("proxyHttpClientManager") HttpClientConnectionManager connectionManager) {
        var connectionMonitor = new IdleConnectionMonitorThread(connectionManager);
        connectionMonitor.setIntervalMilliseconds(SystemProperties.getClientProxyIdleConnectionMonitorInterval());
        connectionMonitor.setConnectionIdleTimeMilliseconds(
                SystemProperties.getClientProxyIdleConnectionMonitorIdleTime());
        return connectionMonitor;
    }

    @Bean(value = "proxyHttpClient", destroyMethod = "close")
    CloseableHttpClient proxyHttpClient(@Qualifier("proxyHttpClientManager") HttpClientConnectionManager connectionManager) {
        log.trace("createClient()");

        int timeout = SystemProperties.getClientProxyTimeout();
        int socketTimeout = SystemProperties.getClientProxyHttpClientTimeout();
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
    HttpClientConnectionManager getClientConnectionManager() throws Exception {
        RegistryBuilder<ConnectionSocketFactory> sfr = RegistryBuilder.create();

        sfr.register("http", PlainConnectionSocketFactory.INSTANCE);

        if (SystemProperties.isSslEnabled()) {
            sfr.register("https", createSSLSocketFactory());
        }

        SocketConfig.Builder sockBuilder = SocketConfig.custom().setTcpNoDelay(true);
        sockBuilder.setSoLinger(SystemProperties.getClientProxyHttpClientSoLinger());
        sockBuilder.setSoTimeout(SystemProperties.getClientProxyHttpClientTimeout());
        SocketConfig socketConfig = sockBuilder.build();

        PoolingHttpClientConnectionManager poolingManager = new PoolingHttpClientConnectionManager(sfr.build());
        poolingManager.setMaxTotal(SystemProperties.getClientProxyPoolTotalMaxConnections());
        poolingManager.setDefaultMaxPerRoute(SystemProperties.getClientProxyPoolDefaultMaxConnectionsPerRoute());
        poolingManager.setDefaultSocketConfig(socketConfig);
        poolingManager.setValidateAfterInactivity(
                SystemProperties.getClientProxyValidatePoolConnectionsAfterInactivityMs());

        return poolingManager;
    }

    private static SSLConnectionSocketFactory createSSLSocketFactory() throws Exception {
        return new FastestConnectionSelectingSSLSocketFactory(SSLContextUtil.createXroadSSLContext()
        );
    }

    public static class ClientUseIdleConnectionMonitorEnabledCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return SystemProperties.isClientUseIdleConnectionMonitor();
        }
    }
}
