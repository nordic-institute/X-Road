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

package org.niis.xroad.common.properties.config.quarkus;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.niis.xroad.common.properties.config.ConfigKey;

import java.util.Map;
import java.util.Set;

/**
 * Publishes the stored overrides of the keys marked {@link ConfigKey#publishedToFramework()} into the
 * SmallRye config tree, so a framework setting that interpolates one — e.g.
 * {@code quarkus.http.port: ${xroad.proxy.health-check-port}} — sees the value an operator stored.
 * Nothing else from {@code configuration_properties} reaches the framework: every other key is read
 * through {@code XRoadConfig}.
 *
 * <p>Ordinal 299 keeps the precedence the retired {@code db-source} had: below system properties (400)
 * and env vars (300), above {@code conf.d} yaml (255), the packaged {@code application.yaml} and the
 * DSL defaults source (100).
 */
public final class XRoadFrameworkConfigSource implements ConfigSource {

    private static final String NAME = "xroad-framework-source";
    private static final int ORDINAL = 299;

    private final Map<String, String> values;

    /**
     * @param values flagged key to stored value
     */
    public XRoadFrameworkConfigSource(Map<String, String> values) {
        this.values = Map.copyOf(values);
    }

    @Override
    public Set<String> getPropertyNames() {
        return values.keySet();
    }

    @Override
    public String getValue(String propertyName) {
        return values.get(propertyName);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getOrdinal() {
        return ORDINAL;
    }
}
