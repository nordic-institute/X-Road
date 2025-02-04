/*
 *  Copyright (c) 2022 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - Initial implementation
 *
 */

package org.eclipse.edc.web.jetty;

import ee.ria.xroad.common.cert.CertChainFactory;

import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;

import org.eclipse.edc.runtime.metamodel.annotation.*;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.spi.WebServer;
import org.eclipse.edc.web.spi.configuration.PortMapping;
import org.eclipse.edc.web.spi.configuration.PortMappingRegistry;
import org.eclipse.edc.web.spi.configuration.WebServiceConfigurer;
import org.niis.xroad.edc.extension.bridge.spring.TlsAuthKeyProvider;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

@Provides({ WebServer.class, JettyService.class })
public class JettyExtension implements ServiceExtension {

    private static final String DEFAULT_PATH = "/api";
    private static final String DEFAULT_CONTEXT_NAME = "default";
    private static final int DEFAULT_PORT = 8181;
    @Deprecated(since = "0.11.0")
    private static final String DEPRECATED_SETTING_PATH = "web.http.default";

    @Configuration
    private JettyConfiguration jettyConfiguration;
    @Configuration
    private DefaultApiConfiguration apiConfiguration;
    @Deprecated(since = "0.11.0")
    @Configuration
    private DeprecatedDefaultApiConfiguration deprecatedApiConfiguration;

    @Setting(key = "edc.web.https.keystore.path", description = "Keystore path", required = false)
    private String keystorePath;
    @Setting(key = "edc.web.https.keystore.type", description = "Keystore type", defaultValue = "PKCS12")
    private String keystoreType;

    private JettyService jettyService;
    private final PortMappingRegistry portMappingRegistry = new PortMappingRegistryImpl();

    @Inject
    private GlobalConfProvider globalConfProvider;
    @Inject
    private TlsAuthKeyProvider tlsAuthKeyProvider;
    @Inject
    private CertChainFactory certChainFactory;

    @Override
    public String name() {
        return "Jetty Service";
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor();

        var deprecatedConfig = context.getConfig().getConfig(DEPRECATED_SETTING_PATH);
        if (deprecatedConfig.getRelativeEntries().isEmpty()) {
            portMappingRegistry.register(new PortMapping(DEFAULT_CONTEXT_NAME, apiConfiguration.port(), apiConfiguration.path()));
        } else {
            monitor.warning("Config group %s has been deprecated, please configure the default api context under web.http".formatted(DEPRECATED_SETTING_PATH));
            portMappingRegistry.register(new PortMapping(DEFAULT_CONTEXT_NAME, deprecatedApiConfiguration.port(), deprecatedApiConfiguration.path()));
        }

        KeyStore ks = null;

        if (jettyConfiguration.xroadTlsEnabled()) {
            jettyService = new JettyService(jettyConfiguration, globalConfProvider, tlsAuthKeyProvider, certChainFactory,
                    monitor, portMappingRegistry);
        } else {
            if (keystorePath != null) {
                try {
                    ks = KeyStore.getInstance(keystoreType);
                    try (var stream = new FileInputStream(keystorePath)) {
                        ks.load(stream, jettyConfiguration.keystorePassword().toCharArray());
                    }
                } catch (KeyStoreException | CertificateException | IOException | NoSuchAlgorithmException e) {
                    throw new EdcException(e);
                }
            }

            jettyService = new JettyService(jettyConfiguration, ks, monitor, portMappingRegistry);
        }
        context.registerService(JettyService.class, jettyService);
        context.registerService(WebServer.class, jettyService);
    }

    @Override
    public void start() {
        jettyService.start();
    }

    @Override
    public void shutdown() {
        if (jettyService != null) {
            jettyService.shutdown();
        }
    }

    @Provider
    @Deprecated(since = "0.11.0")
    public WebServiceConfigurer webServiceContextConfigurator(ServiceExtensionContext context) {
        return new WebServiceConfigurerImpl(context.getMonitor(), portMappingRegistry);
    }

    @Provider
    public PortMappingRegistry portMappings() {
        return portMappingRegistry;
    }

    @Settings
    record DefaultApiConfiguration(
            @Setting(key = "web.http.port", description = "Port for default api context", defaultValue = DEFAULT_PORT + "")
            int port,
            @Setting(key = "web.http.path", description = "Path for default api context", defaultValue = DEFAULT_PATH)
            String path
    ) {

    }

    @Settings
    @Deprecated(since = "0.11.0")
    record DeprecatedDefaultApiConfiguration(
            @Deprecated(since = "0.11.0")
            @Setting(key = "web.http.default.port", description = "Port for default api context", defaultValue = DEFAULT_PORT + "")
            int port,
            @Deprecated(since = "0.11.0")
            @Setting(key = "web.http.default.path", description = "Path for default api context", defaultValue = DEFAULT_PATH)
            String path
    ) {

    }

}
