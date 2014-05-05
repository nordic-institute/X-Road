package ee.cyber.xroad.mediator.client;

import java.io.IOException;

import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.reactor.ConnectingIOReactor;

import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.xroad.mediator.common.HttpClientManager;

import static ee.cyber.sdsb.common.ErrorCodes.translateException;

class HttpClientManagerImpl implements HttpClientManager {

    // TODO: Fine-tune connection parameters
    private static final int CLIENT_TIMEOUT = 300000; // in milliseconds.
    private static final int CLIENT_MAX_TOTAL_CONNECTIONS = 10000;
    private static final int CLIENT_MAX_CONNECTIONS_PER_ROUTE = 2500;

    private CloseableHttpAsyncClient defaultClient;

    @Override
    public CloseableHttpAsyncClient getDefaultHttpClient() {
        if (defaultClient != null) {
            return defaultClient;
        }

        try {
            defaultClient = initHttpClient();
            defaultClient.start();

            return defaultClient;
        } catch (Exception e) {
            throw translateException(e);
        }
    }

    @Override
    public CloseableHttpAsyncClient getHttpClient(ClientId client) {
        return getDefaultHttpClient();
    }

    private CloseableHttpAsyncClient initHttpClient() throws Exception {
        HttpAsyncClientBuilder builder = HttpAsyncClients.custom();

        IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
                .setIoThreadCount(Runtime.getRuntime().availableProcessors())
                .setConnectTimeout(CLIENT_TIMEOUT)
                .setSoTimeout(CLIENT_TIMEOUT)
                .build();

        ConnectingIOReactor ioReactor =
                new DefaultConnectingIOReactor(ioReactorConfig);

        PoolingNHttpClientConnectionManager connManager =
                new PoolingNHttpClientConnectionManager(ioReactor);

        connManager.setMaxTotal(CLIENT_MAX_TOTAL_CONNECTIONS);
        connManager.setDefaultMaxPerRoute(CLIENT_MAX_CONNECTIONS_PER_ROUTE);

        builder.setConnectionManager(connManager);
        return builder.build();
    }

    @Override
    public void shutdown() {
        if (defaultClient != null) {
            try {
                defaultClient.close();
            } catch (IOException e) {
                throw translateException(e);
            }
        }
    }


}
