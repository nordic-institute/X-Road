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
import org.niis.xroad.common.properties.config.ConfigKeyProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Publishes the packaged DSL defaults ({@code ConfigKey.defaultValue()}, already in raw
 * string form) of the given {@link ConfigKeyProvider}s into the Quarkus/SmallRye config
 * tree, so {@code ${xroad.*}} placeholders and {@code @ConfigProperty} resolve without
 * duplicating defaults in YAML.
 *
 * <p>Low ordinal on purpose: defaults are a fallback only — {@code application.yaml},
 * profiles, env vars and the DB config source ({@code db-source}, ordinal 299) all win.
 */
public final class XRoadDefaultsConfigSource implements ConfigSource {

    private static final String NAME = "xroad-defaults-source";
    // below application.yaml (~250), env (300) and db-source (299): DSL defaults only fill gaps
    private static final int ORDINAL = 100;

    private final Map<String, String> values;

    /**
     * @param providers providers whose keys' raw defaults are published
     */
    public XRoadDefaultsConfigSource(List<ConfigKeyProvider> providers) {
        var map = new LinkedHashMap<String, String>();
        for (var provider : providers) {
            provider.keys().forEach(key -> {
                if (key.defaultValue() != null) {
                    map.put(key.key(), key.defaultValue());
                }
            });
        }
        this.values = Map.copyOf(map);
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
    public Map<String, String> getProperties() {
        return values;
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
