package ee.cyber.xroad.mediator.client;

import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;

import ee.ria.xroad.common.identifier.ClientId;

/**
 * This interface provides methods for acquiring HttpClient instances based on
 * client identifier.
 *
 * Implementations of this interface should create a new HttpClient instance
 * for a given ClientId only if it does not already exist.
 */
public interface HttpClientManager {

    /**
     * @return a default instance of http client. Used when the client
     * identifier is not available or does not matter.
     */
    CloseableHttpAsyncClient getDefaultHttpClient();

    /**
     * @param client the client ID
     * @return a new or cached instance of http client for the given client
     * identifier. The created http client could be customized based on the
     * client identifier (such as custom SSL key/trust managers etc.)
     */
    CloseableHttpAsyncClient getHttpClient(ClientId client);

    /**
     * Shuts down (closes) all instances created by this implementation.
     */
    void shutdown();
}
