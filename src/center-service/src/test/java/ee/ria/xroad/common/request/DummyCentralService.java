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
package ee.ria.xroad.common.request;

import java.security.SecureRandom;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.ria.xroad.common.PortNumbers;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.StartStop;

/**
 * Dummy central service.
 */
public class DummyCentralService implements StartStop {

    private static final Logger LOG =
            LoggerFactory.getLogger(DummyCentralService.class);

    static final String HTTP_CONNECTOR_NAME = "HttpConnector";
    static final String HTTPS_CONNECTOR_NAME = "HttpsConnector";

    private Server server = new Server();

    private String listenAddress;

    DummyCentralService() throws Exception {
        listenAddress = "127.0.0.1";

        createConnectors();
        createHandlers();
    }

    @Override
    public void start() throws Exception {
        server.start();
    }

    @Override
    public void stop() throws Exception {
        server.stop();
    }

    @Override
    public void join() throws InterruptedException {
        server.join();
    }

    private void createConnectors() throws Exception {
        SelectChannelConnector httpConnector = new SelectChannelConnector();
        httpConnector.setName(HTTP_CONNECTOR_NAME);
        httpConnector.setPort(PortNumbers.CLIENT_HTTP_PORT);
        httpConnector.setHost(listenAddress);
        server.addConnector(httpConnector);

        SelectChannelConnector httpsConnector = createSslConnector();
        httpsConnector.setName(HTTPS_CONNECTOR_NAME);
        httpsConnector.setPort(PortNumbers.CLIENT_HTTPS_PORT);
        httpsConnector.setHost(listenAddress);
        server.addConnector(httpsConnector);
    }

    private static SslSelectChannelConnector createSslConnector()
            throws Exception {
        SslContextFactory cf = new SslContextFactory(false);
        //cf.setNeedClientAuth(true);

        cf.setIncludeCipherSuites(CryptoUtils.getINCLUDED_CIPHER_SUITES());
        cf.setSessionCachingEnabled(true);

        SSLContext ctx = SSLContext.getInstance(CryptoUtils.SSL_PROTOCOL);

        KeyManager km = new ManagementRequestServerKeyManager();
        ctx.init(new KeyManager[] {km}, null, new SecureRandom());

        cf.setSslContext(ctx);

        return new SslSelectChannelConnector(cf);
    }

    private void createHandlers() {
        HandlerCollection handlers = new HandlerCollection();
        handlers.addHandler(new DummyCentralServiceHandler());
        server.setHandler(handlers);
    }

}
