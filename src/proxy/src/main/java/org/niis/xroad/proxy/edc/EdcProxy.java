/*
 * The MIT License
 *
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

package org.niis.xroad.proxy.edc;

import ee.ria.xroad.common.SystemProperties;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.Slf4jRequestLogWriter;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.eclipse.jetty.xml.XmlConfiguration;
import org.niis.xroad.proxy.configuration.ProxyEdcConfig;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static ee.ria.xroad.common.SystemProperties.DEFAULT_CONNECTOR_HOST;

@Component
@Conditional(ProxyEdcConfig.DataspacesEnabledCondition.class)
@RequiredArgsConstructor
@Slf4j
public class EdcProxy {
    private static final int ACCEPTOR_COUNT = Math.max(2, Runtime.getRuntime().availableProcessors());
    private static final String EDC_CONNECTOR_NAME = "EdcProxyConnector";

    private final AssetAuthorizationCallbackHandler assetAuthorizationCallbackHandler;

    private final Server server = new Server();

    @PostConstruct
    public void init() throws Exception {
        configureServer();
        createConnector();
        createHandlers();

        server.start();
    }

    @PreDestroy
    public void stop() throws Exception {
        server.stop();
    }

    private void configureServer() throws Exception {
        log.trace("configureServer()");

        Path file = Paths.get(SystemProperties.getJettyEdcProxyConfFile());

        log.debug("Configuring server from {}", file);
        new XmlConfiguration(ResourceFactory.root().newResource(file)).configure(server);
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private void createConnector() {
        log.trace("createConnector()");

        int port = SystemProperties.getEdcProxyListenPort();

        // todo: poc. ssl/antidos can be configured
        ServerConnector connector = new ServerConnector(server, ACCEPTOR_COUNT, -1);

        connector.setName(EDC_CONNECTOR_NAME);
        connector.setPort(port);
        connector.setHost(DEFAULT_CONNECTOR_HOST); // todo: parameterize if needed

        connector.setIdleTimeout(30_000); // todo: parameterize if needed

        connector.getConnectionFactories().stream()
                .filter(HttpConnectionFactory.class::isInstance)
                .map(HttpConnectionFactory.class::cast)
                .forEach(httpCf -> {
                    httpCf.getHttpConfiguration().setSendServerVersion(false);
                    Optional.ofNullable(httpCf.getHttpConfiguration().getCustomizer(SecureRequestCustomizer.class))
                            .ifPresent(customizer -> {
                                customizer.setSniHostCheck(false);
                            });
                });

        server.addConnector(connector);

        log.info("EdcProxy {} created ({}:{})", connector.getClass().getSimpleName(), DEFAULT_CONNECTOR_HOST, port);
    }

    private void createHandlers() {
        log.trace("createHandlers()");

        final Slf4jRequestLogWriter writer = new Slf4jRequestLogWriter();
        writer.setLoggerName(getClass().getPackage().getName() + ".RequestLog");
        final CustomRequestLog reqLog = new CustomRequestLog(writer, CustomRequestLog.EXTENDED_NCSA_FORMAT);

        server.setRequestLog(reqLog);

        var handlers = new Handler.Sequence();

        handlers.addHandler(assetAuthorizationCallbackHandler);

        server.setHandler(handlers);
    }

}
