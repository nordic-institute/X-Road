/*
 *  Copyright (c) 2024 Cofinity-X
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Cofinity-X - initial API and implementation
 *
 */

package org.eclipse.edc.web.jetty;

import org.eclipse.edc.web.spi.configuration.PortMapping;
import org.eclipse.edc.web.spi.configuration.PortMappingRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PortMappingRegistryImpl implements PortMappingRegistry {

    private final Map<Integer, PortMapping> portMappings = new HashMap<>();

    @Override
    public void register(PortMapping portMapping) {
        if (!portMapping.path().startsWith("/")) {
            throw new IllegalArgumentException("A context path must start with '/', instead it was: %s ".formatted(portMapping.path()));
        }

        if (portMappings.containsKey(portMapping.port())) {
            throw new IllegalArgumentException("A binding for port %s already exists".formatted(portMapping.port()));
        }
        portMappings.put(portMapping.port(), portMapping);
    }

    @Override
    public List<PortMapping> getAll() {
        return portMappings.values().stream().toList();
    }
}
