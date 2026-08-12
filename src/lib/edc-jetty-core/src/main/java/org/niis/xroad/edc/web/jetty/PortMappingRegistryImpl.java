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
package org.niis.xroad.edc.web.jetty;

import org.eclipse.edc.web.spi.configuration.PortMapping;
import org.eclipse.edc.web.spi.configuration.PortMappingRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Preserves stock EDC's {@code PortMappingRegistryImpl} semantics exactly: one binding per port, keyed
 * by port number, so two contexts can never silently collide on the same connector.
 */
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
