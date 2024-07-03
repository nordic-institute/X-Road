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

import lombok.Getter;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * POJO that contains portmappings for Jetty, consisting of a context alias, a port and a path.
 *
 * @see JettyConfiguration
 * @see JettyService
 */
public class PortMapping {
    private final String alias;
    @Getter
    private final int port;
    @Getter
    private final String path;
    @Getter
    private final boolean needClientAuth;

    public static PortMapping getDefault() {
        return getDefault(JettyConfiguration.DEFAULT_PORT);
    }

    public static PortMapping getDefault(int port) {
        return new PortMapping(JettyConfiguration.DEFAULT_CONTEXT_NAME, port, JettyConfiguration.DEFAULT_PATH, false);
    }

    public PortMapping(String name, int port, String path, boolean needClientAuth) {
        alias = name;
        this.port = port;
        this.path = path;
        this.needClientAuth = needClientAuth;
    }

    public String getName() {
        return alias;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("alias", alias)
                .append("port", port)
                .append("path", path)
                .append("needClientAuth", needClientAuth)
                .toString();
    }
}
