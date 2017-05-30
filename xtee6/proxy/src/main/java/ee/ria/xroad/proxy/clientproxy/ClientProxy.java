/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.proxy.clientproxy;

import ch.qos.logback.access.jetty.RequestLogImpl;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.AuthTrustManager;
import ee.ria.xroad.common.db.HibernateUtil;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.StartStop;
import ee.ria.xroad.proxy.conf.AuthKeyManager;
import ee.ria.xroad.proxy.serverproxy.IdleConnectionMonitorThread;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
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
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.xml.XmlConfiguration;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import static ee.ria.xroad.proxy.clientproxy.HandlerLoader.loadHandler;

/**
 * Client proxy that handles requests of service clients.
 */
@Slf4j
public class ClientProxy implements StartStop {

    // SSL session timeout
    private static final int SSL_SESSION_TIMEOUT = 600;

    private static final String CLIENTPROXY_HANDLERS =
            SystemProperties.PREFIX + "proxy.clientHandlers";

    static final String CLIENT_HTTP_CONNECTOR_NAME = "ClientConnector";
    static final String CLIENT_HTTPS_CONNECTOR_NAME = "ClientSSLConnector";

    private Server server = new Server();

    private CloseableHttpClient client;
    private IdleConnectionMonitorThread connectionMonitor;

    /**
     * Constructs and configures a new client proxy.
     * @throws Exception in case of any errors
     */
    public ClientProxy() throws Exception {
        configureServer();

        createClient();
        createConnectors();
        createHandlers();
    }

    private void configureServer() throws Exception {
        log.trace("configureServer()");

        File configFile = new File(
                SystemProperties.getJettyClientProxyConfFile());

        log.debug("Configuring server from {}", configFile);
        try (InputStream in = new FileInputStream(configFile)) {
            XmlConfiguration config = new XmlConfiguration(in);
            config.configure(server);
        }
    }

    private void createClient() throws Exception {
        log.trace("createClient()");

        int timeout = SystemProperties.getClientProxyTimeout();
        RequestConfig.Builder rb = RequestConfig.custom();
        rb.setConnectTimeout(timeout);
        rb.setConnectionRequestTimeout(timeout);
        rb.setStaleConnectionCheckEnabled(false);

        HttpClientBuilder cb = HttpClients.custom();

        HttpClientConnectionManager connectionManager = getClientConnectionManager();
        cb.setConnectionManager(connectionManager);

        if (SystemProperties.isClientUseIdleConnectionMonitor()) {
            connectionMonitor = new IdleConnectionMonitorThread(connectionManager);
            connectionMonitor.setIntervalMilliseconds(
                    SystemProperties.getClientProxyIdleConnectionMonitorInterval());
            connectionMonitor.setConnectionIdleTimeMilliseconds(
                    SystemProperties.getClientProxyIdleConnectionMonitorTimeout());
        }

        cb.setDefaultRequestConfig(rb.build());

        // Disable request retry
        cb.setRetryHandler(new DefaultHttpRequestRetryHandler(0, false));

        client = cb.build();
    }

    private HttpClientConnectionManager getClientConnectionManager() throws Exception {
        RegistryBuilder<ConnectionSocketFactory> sfr =
                RegistryBuilder.create();

        sfr.register("http", PlainConnectionSocketFactory.INSTANCE);

        if (SystemProperties.isSslEnabled()) {
            sfr.register("https", createSSLSocketFactory());
        }

        SocketConfig.Builder sockBuilder =  SocketConfig.custom().setTcpNoDelay(true);
        sockBuilder.setSoLinger(SystemProperties.getClientProxyHttpClientSoLinger());
        sockBuilder.setSoTimeout(SystemProperties.getClientProxyHttpClientMaxIdleTime());
        SocketConfig socketConfig = sockBuilder.build();

        if (SystemProperties.isClientUseConnectionPoolForServerConnections()) {

            PoolingHttpClientConnectionManager poolingManager =
                    new PoolingHttpClientConnectionManager(sfr.build());
            poolingManager.setMaxTotal(SystemProperties.getProxyPoolTotalMaxConnections());
            poolingManager.setDefaultMaxPerRoute(SystemProperties.getProxyPoolDefaultMaxConnectionsPerRoute());
            poolingManager.setDefaultSocketConfig(socketConfig);
            return poolingManager;
        } else {
            BasicHttpClientConnectionManager basicManager =
                    new BasicHttpClientConnectionManager(sfr.build());
            basicManager.setSocketConfig(socketConfig);
            return basicManager;
        }
    }

    private static SSLConnectionSocketFactory createSSLSocketFactory()
            throws Exception {
        SSLContext ctx = SSLContext.getInstance(CryptoUtils.SSL_PROTOCOL);
        ctx.init(new KeyManager[] {AuthKeyManager.getInstance()},
                new TrustManager[] {new AuthTrustManager()},
                new SecureRandom());
        return new FastestConnectionSelectingSSLSocketFactory(ctx,
                CryptoUtils.getINCLUDED_CIPHER_SUITES());
    }

    private void createConnectors() throws Exception {
        log.trace("createConnectors()");

        createClientHttpConnector(SystemProperties.getConnectorHost(),
                SystemProperties.getClientProxyHttpPort());

        createClientHttpsConnector(SystemProperties.getConnectorHost(),
                SystemProperties.getClientProxyHttpsPort());
    }

    private void createClientHttpConnector(String hostname, int port) {
        log.trace("createClientHttpConnector({}, {})", hostname, port);

        SelectChannelConnector connector = new SelectChannelConnector();

        connector.setName(CLIENT_HTTP_CONNECTOR_NAME);
        connector.setHost(hostname);
        connector.setPort(port);

        connector.setSoLingerTime(SystemProperties.getClientProxyConnectorSoLinger());
        connector.setMaxIdleTime(SystemProperties.getClientProxyConnectorMaxIdleTime());

        connector.setAcceptors(Runtime.getRuntime().availableProcessors());
        server.setSendServerVersion(false);
        server.addConnector(connector);

        log.info("Client HTTP connector created ({}:{})", hostname, port);
    }

    private void createClientHttpsConnector(String hostname, int port)
            throws Exception {
        log.trace("createClientHttpsConnector({}, {})", hostname, port);

        SslContextFactory cf = new SslContextFactory(false);
        cf.setWantClientAuth(true);
        cf.setSessionCachingEnabled(true);
        cf.setSslSessionTimeout(SSL_SESSION_TIMEOUT);
        cf.setIncludeProtocols(SystemProperties.getProxyClientTLSProtocols());
        cf.setIncludeCipherSuites(SystemProperties.getProxyClientTLSCipherSuites());

        SSLContext ctx = SSLContext.getInstance(CryptoUtils.SSL_PROTOCOL);
        ctx.init(new KeyManager[] {new ClientSslKeyManager()},
                new TrustManager[] {new ClientSslTrustManager()},
                new SecureRandom());

        cf.setSslContext(ctx);

        SslSelectChannelConnector connector =
                new SslSelectChannelConnector(cf);

        connector.setName(CLIENT_HTTPS_CONNECTOR_NAME);
        connector.setHost(hostname);
        connector.setPort(port);

        connector.setSoLingerTime(SystemProperties.getClientProxyConnectorSoLinger());
        connector.setMaxIdleTime(SystemProperties.getClientProxyConnectorMaxIdleTime());

        connector.setAcceptors(Runtime.getRuntime().availableProcessors());

        server.addConnector(connector);

        log.info("Client HTTPS connector created ({}:{})", hostname, port);
    }

    private void createHandlers() throws Exception {
        log.trace("createHandlers()");

        RequestLogImpl reqLog = new RequestLogImpl();
        reqLog.setResource("/logback-access-clientproxy.xml");
        reqLog.setQuiet(true);

        RequestLogHandler logHandler = new RequestLogHandler();
        logHandler.setRequestLog(reqLog);

        HandlerCollection handlers = new HandlerCollection();

        handlers.addHandler(logHandler);

        for (Handler handler : getClientHandlers()) {
            handlers.addHandler(handler);
        }

        server.setHandler(handlers);
    }

    private List<Handler> getClientHandlers() {
        List<Handler> handlers = new ArrayList<>();

        String handlerClassNames = System.getProperty(CLIENTPROXY_HANDLERS);
        if (!StringUtils.isBlank(handlerClassNames)) {
            for (String handlerClassName : handlerClassNames.split(",")) {
                try {
                    log.trace("Loading client handler {}", handlerClassName);
                    handlers.add(loadHandler(handlerClassName, client));
                } catch (Exception e) {
                    throw new RuntimeException(
                            "Failed to load client handler: "
                                    + handlerClassName, e);
                }
            }
        }

        log.trace("Loading default client handler");
        handlers.add(new ClientMessageHandler(client)); // default handler
        return handlers;
    }

    @Override
    public void start() throws Exception {
        log.trace("start()");

        server.start();
        if (connectionMonitor != null) {
            connectionMonitor.start();
        }
    }

    @Override
    public void join() throws InterruptedException {
        log.trace("join()");

        if (server.getThreadPool() != null) {
            server.join();
        }
    }

    @Override
    public void stop() throws Exception {
        log.trace("stop()");

        if (connectionMonitor != null) {
            connectionMonitor.shutdown();
        }

        client.close();
        server.stop();

        HibernateUtil.closeSessionFactories();
    }

    private static class ClientSslTrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

    }
}
