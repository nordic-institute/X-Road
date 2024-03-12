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

package org.niis.xroad.edc.extension.signer;

import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.jetty.JettyConfiguration;
import org.eclipse.edc.web.jetty.JettyService;
import org.eclipse.edc.web.spi.WebServer;
import org.eclipse.edc.web.spi.configuration.WebServiceConfigurer;
import org.eclipse.edc.web.spi.configuration.WebServiceConfigurerImpl;

@Provides({WebServer.class, JettyService.class})
public class XrdJettyExtension implements ServiceExtension {

    @Setting
    private static final String KEYSTORE_PASSWORD = "edc.web.https.keystore.password";
    @Setting
    private static final String KEYMANAGER_PASSWORD = "edc.web.https.keymanager.password";

    private JettyService jettyService;

    @Override
    public String name() {
        return "X-Road customized Jetty Service";
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor();

        var configuration = JettyConfiguration.createFromConfig(
                context.getSetting(KEYSTORE_PASSWORD, "password"),
                context.getSetting(KEYMANAGER_PASSWORD, "password"), context.getConfig());

        jettyService = new JettyService(configuration, monitor);
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
    public WebServiceConfigurer webServiceContextConfigurator() {
        return new WebServiceConfigurerImpl();
    }

}
