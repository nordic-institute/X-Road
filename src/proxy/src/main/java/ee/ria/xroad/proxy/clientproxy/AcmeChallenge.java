/*
 * The MIT License
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
package ee.ria.xroad.proxy.clientproxy;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.StartStop;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.Slf4jRequestLogWriter;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

@Slf4j
public class AcmeChallenge implements StartStop {

    private static final int ACCEPTOR_COUNT = Runtime.getRuntime().availableProcessors();
    private static final String CLIENT_HTTP_CONNECTOR_NAME = "AcmeClientConnector";
    private final Server server = new Server();

    public AcmeChallenge() {
        configureServer();
        createConnectors();
        createHandlers();
    }

    private void configureServer() {
        final var writer = new Slf4jRequestLogWriter();
        writer.setLoggerName(getClass().getPackage().getName() + ".RequestLog");
        final var reqLog = new CustomRequestLog(writer, CustomRequestLog.EXTENDED_NCSA_FORMAT
                + " \"%{X-Forwarded-For}i\"");
        server.setRequestLog(reqLog);
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
        if (server.getThreadPool() != null) {
            server.join();
        }
    }

    private void createConnectors() {
        createClientHttpConnector();
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private void createClientHttpConnector() {
        String hostname = SystemProperties.getConnectorHost();
        int port = 80;
        ServerConnector connector = new ServerConnector(server, ACCEPTOR_COUNT, -1);
        connector.setName(CLIENT_HTTP_CONNECTOR_NAME);
        connector.setHost(hostname);
        connector.setPort(port);
        connector.setIdleTimeout(SystemProperties.getClientProxyConnectorInitialIdleTime());
        applyConnectionFactoryConfig(connector);
        server.addConnector(connector);
        log.info("ACME Client HTTP connector created ({}:{})", hostname, port);
    }

    private void applyConnectionFactoryConfig(ServerConnector connector) {
        connector.getConnectionFactories().stream()
                .filter(HttpConnectionFactory.class::isInstance)
                .map(HttpConnectionFactory.class::cast)
                .forEach(httpCF -> {
                    httpCF.getHttpConfiguration().setSendServerVersion(false);
                    Optional.ofNullable(httpCF.getHttpConfiguration().getCustomizer(SecureRequestCustomizer.class))
                            .ifPresent(customizer -> customizer.setSniHostCheck(false));
                });
    }

    private void createHandlers() {
        server.setHandler(createAcmeChallengeContextHandler());
    }

    private ContextHandler createAcmeChallengeContextHandler() {
        ContextHandler contextHandler = new ContextHandler("/.well-known/acme-challenge");
        ResourceHandler resourceHandler = new ResourceHandler();
        Path baseDir = Path.of("/etc/xroad/acme-challenge");
        File baseDirFile = baseDir.toFile();
        if (!baseDirFile.exists()) {
            baseDirFile.mkdir();
        }
        Resource baseResource = ResourceFactory.of(resourceHandler).newResource(baseDir);
        resourceHandler.setBaseResource(baseResource);
        contextHandler.setHandler(resourceHandler);
        return contextHandler;
    }
}
