/*
 *  Copyright (c) 2020 - 2022 Microsoft Corporation
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

import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.spi.WebServer;
import org.eclipse.edc.web.spi.configuration.WebServiceConfiguration;
import org.eclipse.edc.web.spi.configuration.WebServiceConfigurer;
import org.eclipse.edc.web.spi.configuration.WebServiceSettings;

import static java.lang.String.format;

public class WebServiceConfigurerImpl implements WebServiceConfigurer {


    @Override
    public WebServiceConfiguration configure(ServiceExtensionContext context, WebServer webServer, WebServiceSettings settings) {

        var apiConfig = settings.apiConfigKey();
        var config = context.getConfig(apiConfig);
        var port = settings.getDefaultPort();
        var path = settings.getDefaultPath();
        var contextAlias = settings.getContextAlias();
        var monitor = context.getMonitor();

        if (!config.getEntries().isEmpty()) {
            port = config.getInteger("port", port);
            path = config.getString("path", path);
        } else {
            monitor.warning(format("Settings for [%s] and/or [%s] were not provided. Using default"
                    + " value(s) instead.", apiConfig + ".path", apiConfig + ".path"));

            if (settings.useDefaultContext()) {
                contextAlias = webServer.getDefaultContextName();
            } else {
                webServer.addPortMapping(contextAlias, port, path);
            }
        }

        monitor.info(format("%s will be available under port=%s, path=%s", settings.getName(), port, path));

        return WebServiceConfiguration.Builder.newInstance()
                .path(path)
                .contextAlias(contextAlias)
                .port(port)
                .build();

    }
}
