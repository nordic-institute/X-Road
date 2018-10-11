/**
 * The MIT License
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
package ee.ria.xroad.proxy;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.AuthTrustManager;
import ee.ria.xroad.common.db.HibernateUtil;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.StartStop;
import ee.ria.xroad.proxy.conf.AuthKeyManager;
import ee.ria.xroad.proxy.serverproxy.IdleConnectionMonitorThread;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.impl.client.CloseableHttpClient;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.xml.XmlConfiguration;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Collection;

/**
 * X-Road proxy base class
 */
@Slf4j
public abstract class XroadProxy implements StartStop {

    // SSL session timeout in seconds
    protected static final int SSL_SESSION_TIMEOUT = 600;

    @Getter
    private Server server;

    @Getter
    private CloseableHttpClient client;

    @Getter
    private IdleConnectionMonitorThread connectionMonitor;

    /**
     *
     * @throws Exception
     */
    public XroadProxy() throws Exception {
        server = new Server();
        initServer();
        client = createClient();
        connectionMonitor = createConnectionMonitor();
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

    /**
     *
     * @return
     * @throws KeyManagementException
     * @throws NoSuchAlgorithmException
     */
    public SSLContext createSSLContext() throws KeyManagementException, NoSuchAlgorithmException {
        SSLContext ctx = SSLContext.getInstance(CryptoUtils.SSL_PROTOCOL);
        ctx.init(new KeyManager[] {AuthKeyManager.getInstance()}, new TrustManager[] {new AuthTrustManager()},
                new SecureRandom());
        return ctx;
    }

    /**
     *
     * @return
     */
    public String[] getAcceptedCipherSuites() {
        return SystemProperties.getXroadTLSCipherSuites();
    }

    protected abstract IdleConnectionMonitorThread createConnectionMonitor() throws Exception;

    protected abstract CloseableHttpClient createClient() throws Exception;

    protected abstract Path getJettyServerConfFilePath();

    protected abstract Collection<ServerConnector> createConnectors() throws Exception;

    protected abstract AbstractHandler createHandlers();

    private Server initServer() throws Exception {

        log.trace("configureServer()");

        Path file = getJettyServerConfFilePath();

        log.debug("Configuring server from {}", file);

        try (InputStream in = Files.newInputStream(file)) {
            new XmlConfiguration(in).configure(server);
        }

        for (Connector connector : createConnectors()) {
            server.addConnector(connector);
        }

        server.setHandler(createHandlers());
        return server;
    }

}
