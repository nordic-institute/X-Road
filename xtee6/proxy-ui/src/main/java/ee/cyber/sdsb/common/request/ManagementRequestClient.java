package ee.cyber.sdsb.common.request;

import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.client.HttpClient;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.util.CryptoUtils;
import ee.cyber.sdsb.common.util.HttpSender;
import ee.cyber.sdsb.common.util.StartStop;

import static ee.cyber.sdsb.common.PortNumbers.CLIENT_HTTPS_PORT;

/**
 * Client that sends managements requests to the Central Server.
 */
public class ManagementRequestClient implements StartStop {

    private static final Logger LOG =
            LoggerFactory.getLogger(ManagementRequestClient.class);

    // Configuration parameters.
    // TODO: Make configurable?
    private static final int CLIENT_TIMEOUT = 300000; // 30 sec.
    private static final int CLIENT_MAX_TOTAL_CONNECTIONS = 100;
    private static final int CLIENT_MAX_CONNECTIONS_PER_ROUTE = 25;

    private HttpClient httpClient;

    private static ManagementRequestClient instance =
            new ManagementRequestClient();

    public static ManagementRequestClient getInstance() {
        return instance;
    }

    private ManagementRequestClient() {
        try {
            createClient();
        } catch (Exception e) {
            throw new RuntimeException(
                    "Unable to initialize management request client", e);
        }
    }

    @Override
    public void start() throws Exception {
        LOG.info("Starting ManagementRequestClient...");
    }

    @Override
    public void stop() throws Exception {
        LOG.info("Stopping ManagementRequestClient...");
        httpClient.getConnectionManager().shutdown();
    }

    @Override
    public void join() throws InterruptedException {
    }

    // -- Helper methods ------------------------------------------------------

    HttpSender createHttpSender() {
        return new HttpSender(httpClient);
    }

    private void createClient() throws Exception {
        PoolingClientConnectionManager connMgr =
                new PoolingClientConnectionManager();
        connMgr.setMaxTotal(CLIENT_MAX_TOTAL_CONNECTIONS);
        connMgr.setDefaultMaxPerRoute(CLIENT_MAX_CONNECTIONS_PER_ROUTE);

        httpClient = new DefaultHttpClient(connMgr);

        HttpParams params = httpClient.getParams();
        HttpConnectionParams.setConnectionTimeout(params, CLIENT_TIMEOUT);

        // Disable request retry
        ((DefaultHttpClient) httpClient).setHttpRequestRetryHandler(
                new DefaultHttpRequestRetryHandler(0, false));
        addSslSupport(httpClient);

    }

    private static void addSslSupport(HttpClient client) throws Exception {
        SSLContext ctx = SSLContext.getInstance(CryptoUtils.SSL_PROTOCOL);

        TrustManager tm = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain,
                    String authType) throws CertificateException {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain,
                    String authType) throws CertificateException {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }
        };
        ctx.init(null, new TrustManager[] { tm }, new SecureRandom());

        SSLSocketFactory socketFactory = new SSLSocketFactory(ctx,
                SSLSocketFactory.STRICT_HOSTNAME_VERIFIER) {
            @Override
            protected void prepareSocket(SSLSocket sock) throws IOException {
                sock.setEnabledCipherSuites(CryptoUtils.INCLUDED_CIPHER_SUITES);
            }
        };

        Scheme https = new Scheme("https", CLIENT_HTTPS_PORT, socketFactory);
        client.getConnectionManager().getSchemeRegistry().register(https);
    }
}
