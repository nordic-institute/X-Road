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
import org.eclipse.edc.web.spi.configuration.PortMapping;
import org.eclipse.edc.web.spi.configuration.PortMappingRegistry;
import org.eclipse.edc.web.spi.configuration.WebServiceConfiguration;
import org.eclipse.edc.web.spi.configuration.WebServiceConfigurer;
import org.eclipse.edc.web.spi.configuration.WebServiceSettings;

import static java.lang.String.format;

@Deprecated(since = "0.11.0")
public class WebServiceConfigurerImpl implements WebServiceConfigurer {

    private final Monitor monitor;
    private final PortMappingRegistry portMappingRegistry;

    public WebServiceConfigurerImpl(Monitor monitor, PortMappingRegistry portMappingRegistry) {
        this.monitor = monitor;
        this.portMappingRegistry = portMappingRegistry;
    }

    @Override
    public WebServiceConfiguration configure(Config config, WebServiceSettings settings) {
        var apiConfig = settings.apiConfigKey();
        var contextAlias = settings.getContextAlias();
        var port = settings.getDefaultPort();
        var path = settings.getDefaultPath();

        if (config.getEntries().isEmpty()) {
            monitor.warning("Settings for [%s] and/or [%s] were not provided. Using default value(s) instead."
                    .formatted(apiConfig + ".path", apiConfig + ".path"));
        } else {
            port = config.getInteger("port", port);
            path = config.getString("path", path);
        }

        portMappingRegistry.register(new PortMapping(contextAlias, port, path));

        monitor.debug(format("%s API will be available under port=%s, path=%s", contextAlias, port, path));

        return WebServiceConfiguration.Builder.newInstance()
                .path(path)
                .port(port)
                .build();
    }
}
