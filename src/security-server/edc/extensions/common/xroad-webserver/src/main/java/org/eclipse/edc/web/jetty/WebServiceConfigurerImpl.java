/*
 *  Copyright (c) 2022 Microsoft Corporation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Microsoft Corporation - initial API and implementation
 *
 */

package org.eclipse.edc.web.jetty;


import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.web.spi.WebServer;
import org.eclipse.edc.web.spi.configuration.WebServiceConfiguration;
import org.eclipse.edc.web.spi.configuration.WebServiceConfigurer;
import org.eclipse.edc.web.spi.configuration.WebServiceSettings;

import static java.lang.String.format;

public class WebServiceConfigurerImpl implements WebServiceConfigurer {

    private final Monitor monitor;

    public WebServiceConfigurerImpl(Monitor monitor) {
        this.monitor = monitor;
    }

    @Override
    public WebServiceConfiguration configure(Config config, WebServer webServer, WebServiceSettings settings) {
        var apiConfig = settings.apiConfigKey();
        var port = settings.getDefaultPort();
        var path = settings.getDefaultPath();
        var contextAlias = settings.getContextAlias();

        if (!config.getEntries().isEmpty()) {
            port = config.getInteger("port", port);
            path = config.getString("path", path);
        } else {
            monitor.warning(format("Settings for [%s] and/or [%s] were not provided. Using default"
                    + " value(s) instead.", apiConfig + ".path", apiConfig + ".path"));

            if (!settings.useDefaultContext()) {
                webServer.addPortMapping(contextAlias, port, path);
            }
        }

        monitor.info(format("%s will be available under port=%s, path=%s", settings.getName(), port, path));

        return WebServiceConfiguration.Builder.newInstance()
                .path(path)
                .port(port)
                .build();
    }
}
