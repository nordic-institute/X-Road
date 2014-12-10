package ee.cyber.sdsb.proxy.clientproxy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.security.SecureRandom;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.conf.globalconf.AuthTrustManager;
import ee.cyber.sdsb.common.util.CryptoUtils;
import ee.cyber.sdsb.common.util.HttpSender;
import ee.cyber.sdsb.common.util.SystemMetrics;
import ee.cyber.sdsb.proxy.clientproxy.FastestSocketSelector.SocketInfo;
import ee.cyber.sdsb.proxy.conf.AuthKeyManager;

import static ee.cyber.sdsb.common.util.AbstractHttpSender.CHUNKED_LENGTH;
import static ee.cyber.sdsb.proxy.clientproxy.FastestConnectionSelectingSSLSocketFactory.ID_TARGETS;

@Slf4j
class FastestConnectionSelectingSSLSocketFactoryIntegrationTest {

    private static final int CLIENT_MAX_TOTAL_CONNECTIONS = 10000;
    private static final int CLIENT_MAX_CONNECTIONS_PER_ROUTE = 2500;

    private static CloseableHttpClient client;

    public static void main(String[] args) throws Exception {
        URI[] addresses = {
                new URI("https://localhost:8081"),
                new URI("https://localhost:8082"),
                new URI("https://localhost:8080")
        };

        logFH();

        testWithSender(addresses);
        //testFastestSocketSelector(addresses);
    }

    private static void testWithSender(URI[] addresses) throws Exception {
        createClient();

        InputStream content =
                new ByteArrayInputStream("Hello world".getBytes());
        for (int i = 0; i < 10; i++) {
            try (HttpSender sender = new HttpSender(client);) {
                sender.setAttribute(ID_TARGETS, addresses);
                sender.setTimeout(10);
                sender.doPost(new URI("https://localhost:1234"), content,
                        CHUNKED_LENGTH, "text/plain");
            } catch (Exception e) {

            }

            logFH();
        }
    }

    private static void testFastestSocketSelector(URI[] addresses)
            throws Exception {
        for (int i = 0; i < 10; i++) {
            FastestSocketSelector s = new FastestSocketSelector(addresses, 10);
            SocketInfo si = s.select();

            if (si != null) {
                si.getSocket().close();
            }

            logFH();
        }
    }

    private static void createClient() throws Exception {
        RegistryBuilder<ConnectionSocketFactory> socketFactoryRegistry =
                RegistryBuilder.<ConnectionSocketFactory>create();

        socketFactoryRegistry.register("http",
                PlainConnectionSocketFactory.INSTANCE);

        socketFactoryRegistry.register("https", createSSLSocketFactory());

        PoolingHttpClientConnectionManager connMgr =
                new PoolingHttpClientConnectionManager(
                        socketFactoryRegistry.build());
        connMgr.setMaxTotal(CLIENT_MAX_TOTAL_CONNECTIONS);
        connMgr.setDefaultMaxPerRoute(CLIENT_MAX_CONNECTIONS_PER_ROUTE);

        int timeout = SystemProperties.getClientProxyTimeout();
        RequestConfig.Builder rb = RequestConfig.custom();
        rb.setConnectTimeout(timeout);
        rb.setConnectionRequestTimeout(timeout);
        rb.setStaleConnectionCheckEnabled(false);

        HttpClientBuilder cb = HttpClients.custom();
        cb.setConnectionManager(connMgr);
        cb.setDefaultRequestConfig(rb.build());

        // Disable request retry
        cb.setRetryHandler(new DefaultHttpRequestRetryHandler(0, false));

        client = cb.build();
    }

    private static SSLConnectionSocketFactory createSSLSocketFactory()
            throws Exception {
        SSLContext ctx = SSLContext.getInstance(CryptoUtils.SSL_PROTOCOL);
        ctx.init(new KeyManager[] { AuthKeyManager.getInstance() },
                new TrustManager[] { AuthTrustManager.getInstance() },
                new SecureRandom());

        return new FastestConnectionSelectingSSLSocketFactory(ctx,
                        CryptoUtils.INCLUDED_CIPHER_SUITES);
    }

    private static void logFH() {
        log.info("\nfree fd = {}\n",
                SystemMetrics.getFreeFileDescriptorCount());
    }
}
