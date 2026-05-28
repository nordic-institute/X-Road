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

package org.niis.xroad.common.properties.config.impl;

import org.niis.xroad.common.properties.config.ConfigKey;
import org.niis.xroad.common.properties.config.ConfigKeyProvider;
import org.niis.xroad.common.properties.config.Country;
import org.niis.xroad.common.properties.config.Source;
import org.niis.xroad.common.properties.config.Value;
import org.niis.xroad.common.properties.config.XRoadConfig;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Construction entry point for the resolver. Callers depend on {@code :lib:properties-api}
 * for the {@link XRoadConfig} interface they consume, and on {@code :lib:properties-impl}
 * here only at the construction site.
 */
public final class XRoadConfigBuilder {

    private final List<ConfigKeyProvider> providers = new ArrayList<>();
    private Map<String, String> overrides = Map.of();

    private XRoadConfigBuilder() {
    }

    /** @return new builder */
    public static XRoadConfigBuilder create() {
        return new XRoadConfigBuilder();
    }

    /**
     * @param provider provider to register
     * @return this builder
     */
    public XRoadConfigBuilder register(ConfigKeyProvider provider) {
        providers.add(provider);
        return this;
    }

    /**
     * Mocked DB-override layer: dotted effective key &rarr; raw string value.
     *
     * @param raw effective key to raw value
     * @return this builder
     */
    public XRoadConfigBuilder overrides(Map<String, String> raw) {
        this.overrides = Map.copyOf(raw);
        return this;
    }

    /** @return resolved configuration with values cached at build time */
    public XRoadConfig build() {
        return new DefaultXRoadConfig(providers, overrides);
    }

    /**
     * Eager resolver. Resolves every registered key at construction (DB override &rarr;
     * packaged default) and caches the resulting {@link Value}s; v1 does not reread until
     * a new instance is built.
     */
    private static final class DefaultXRoadConfig implements XRoadConfig {

        private final Map<ConfigKey<?>, Value<?>> resolved;

        DefaultXRoadConfig(List<ConfigKeyProvider> providers, Map<String, String> overrides) {
            var map = new LinkedHashMap<ConfigKey<?>, Value<?>>();
            for (var provider : providers) {
                for (var key : provider.keys()) {
                    map.put(key, resolve(key, overrides));
                }
            }
            this.resolved = map;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Value<T> get(ConfigKey<T> key) {
            var value = resolved.get(key);
            if (value == null) {
                throw new IllegalArgumentException("Key not registered: " + key.key());
            }
            return (Value<T>) value;
        }

        @Override
        public Stream<Value<?>> all() {
            return resolved.values().stream();
        }

        @Override
        public Stream<Value<?>> all(String scope) {
            return resolved.values().stream().filter(v -> v.key().scope().equals(scope));
        }

        @Override
        public Optional<Country> country() {
            return Optional.empty();
        }

        private static <T> Value<T> resolve(ConfigKey<T> key, Map<String, String> overrides) {
            var raw = overrides.get(key.key());
            if (raw != null) {
                return new Value<>(key, coerce(raw, key.type(), key.key()), key.defaultValue(), Source.DB);
            }
            return new Value<>(key, key.defaultValue(), key.defaultValue(), Source.DEFAULT);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static <T> T coerce(String raw, Class<T> type, String keyName) {
            try {
                if (type == String.class) {
                    return (T) raw;
                }
                if (type == Integer.class) {
                    return (T) Integer.valueOf(raw);
                }
                if (type == Boolean.class) {
                    return (T) Boolean.valueOf(raw);
                }
                if (type == Duration.class) {
                    return (T) Duration.parse(raw);
                }
                if (type.isEnum()) {
                    return (T) Enum.valueOf((Class) type, raw);
                }
            } catch (RuntimeException e) {
                throw new IllegalStateException("Cannot coerce override for %s to %s: %s"
                        .formatted(keyName, type.getSimpleName(), raw), e);
            }
            throw new IllegalStateException("Unsupported type for %s: %s".formatted(keyName, type.getName()));
        }
    }
}
