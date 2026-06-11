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

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.properties.config.ConfigKey;
import org.niis.xroad.common.properties.config.ConfigKeyProvider;
import org.niis.xroad.common.properties.config.Country;
import org.niis.xroad.common.properties.config.DeploymentMode;
import org.niis.xroad.common.properties.config.Source;
import org.niis.xroad.common.properties.config.Value;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.dbsource.CachedDbConfigSource;
import org.niis.xroad.common.properties.dbsource.DbSourceConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Construction entry point for the resolver. Callers depend on {@code :lib:properties-api}
 * for the {@link XRoadConfig} interface they consume, and on {@code :lib:properties-impl}
 * here only at the construction site.
 */
@Slf4j
public final class XRoadConfigBuilder {

    private final List<ConfigKeyProvider> providers = new ArrayList<>();
    private Map<String, String> overrides = Map.of();
    private DeploymentMode deploymentMode = DeploymentMode.NATIVE;

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
     * In-memory DB-override layer (mainly for tests): dotted effective key &rarr; raw string value.
     * @param raw effective key to raw value
     * @return this builder
     */
    public XRoadConfigBuilder overrides(Map<String, String> raw) {
        this.overrides = Map.copyOf(raw);
        return this;
    }

    /**
     * Sets the deployment mode. In {@link DeploymentMode#CONTAINERIZED} a key resolves to its
     * container default (when declared) instead of the regular default; DB overrides still win.
     * Defaults to {@link DeploymentMode#NATIVE}. Apps derive the value from their framework profile.
     *
     * @param mode deployment mode
     * @return this builder
     */
    public XRoadConfigBuilder deploymentMode(DeploymentMode mode) {
        this.deploymentMode = mode;
        return this;
    }

    /**
     * Loads the DB-override layer from the {@code configuration_properties} table via the
     * {@code DB_CONFIG_SOURCE_*} environment. No-op when the DB config source is disabled or
     * its URL is unset, so callers fall back to packaged defaults. Reuses the framework-neutral
     * {@link CachedDbConfigSource}.
     *
     * @param appName application name (drives the read-pool name and scope filtering)
     * @return this builder
     */
    public XRoadConfigBuilder dbOverrides(String appName) {
        var dbSourceConfig = DbSourceConfig.loadValues(appName);
        if (dbSourceConfig.isEnabled() && dbSourceConfig.getUrl() != null) {
            var dbProps = new CachedDbConfigSource(dbSourceConfig).getProperties();
            var merged = new java.util.LinkedHashMap<>(overrides);
            merged.putAll(dbProps);
            this.overrides = Map.copyOf(merged);
            log.info("Loaded {} config override(s) from DB for '{}'", dbProps.size(), appName);
        } else {
            log.info("DB config source disabled; '{}' uses packaged defaults only", appName);
        }
        return this;
    }

    /** @return resolved configuration with values cached at build time */
    public XRoadConfig build() {
        return new DefaultXRoadConfig(providers, overrides, deploymentMode);
    }

    /**
     * Eager resolver. Resolves every registered key at construction (DB override &rarr;
     * packaged default) and caches the resulting {@link Value}s; v1 does not reread until
     * a new instance is built.
     */
    private static final class DefaultXRoadConfig implements XRoadConfig {

        private final Map<ConfigKey<?>, Value<?>> resolved;

        DefaultXRoadConfig(List<ConfigKeyProvider> providers, Map<String, String> overrides, DeploymentMode deploymentMode) {
            var map = new LinkedHashMap<ConfigKey<?>, Value<?>>();
            for (var provider : providers) {
                for (var key : provider.keys()) {
                    map.put(key, resolve(key, overrides, deploymentMode));
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
        @SuppressWarnings("unchecked")
        public <T> Optional<Value<T>> getOpt(ConfigKey<T> key) {
            return Optional.ofNullable((Value<T>) resolved.get(key));
        }

        @Override
        public Optional<Country> country() {
            return Optional.empty();
        }

        private static <T> Value<T> resolve(ConfigKey<T> key, Map<String, String> overrides, DeploymentMode mode) {
            var raw = overrides.get(key.key());
            if (raw != null) {
                return new Value<>(key, converAndValidate(raw, key), Source.DB);
            }
            var useContainerDefault = mode == DeploymentMode.CONTAINERIZED && key.containerDefaultValue() != null;
            var converted = useContainerDefault ? key.convertedContainerDefaultValue() : key.convertedDefaultValue();
            return new Value<>(key, converted, Source.DEFAULT);
        }

        private static <T> T converAndValidate(String raw, ConfigKey<T> key) {
            var converted = key.convert(raw);
            var result = key.validate(converted);
            if (!result.valid()) {
                throw new IllegalArgumentException(
                        "Invalid config value \"%s\" for %s: %s".formatted(raw, key.key(), result.message()));
            }
            return converted;
        }
    }
}
