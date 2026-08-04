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
package org.niis.xroad.common.properties.config;

import org.niis.xroad.common.properties.dbsource.CachedDbConfigSource;
import org.niis.xroad.common.properties.dbsource.DbSourceConfig;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves the keys marked {@link ConfigKey#publishedToFramework()} so the framework's own
 * configuration can see them.
 *
 * <p>A packaged {@code application.yaml} sometimes interpolates a DSL key into a framework setting
 * ({@code quarkus.http.port: ${xroad.proxy.health-check-port}},
 * {@code spring.servlet.multipart.max-file-size: ${xroad.proxy-ui-api.request-size-limit-binary-upload}}).
 * Those settings are read by Quarkus and Spring themselves, not through {@link XRoadConfig}, so the
 * resolved value has to reach the framework config tree — but nothing else should. The flag keeps that
 * list explicit and reviewable instead of projecting the whole {@code configuration_properties} table.
 */
public final class FrameworkPublishedConfig {

    private FrameworkPublishedConfig() {
    }

    /**
     * Reads the stored overrides of the flagged keys straight from {@code configuration_properties}.
     *
     * <p>Only stored overrides are returned — never declared defaults. The framework already sees the
     * defaults through its own packaged configuration (and, on Quarkus, the DSL defaults source), so
     * publishing them again at a higher precedence would let a default outrank a value an operator put
     * in {@code conf.d}.
     *
     * @param appName   application name partitioning the stored overrides
     * @param providers providers to scan for flagged keys
     * @return flagged key to stored value; empty when nothing is flagged, no DB is configured, or no
     *         flagged key has a stored override
     */
    public static Map<String, String> storedOverrides(String appName, List<ConfigKeyProvider> providers) {
        Set<String> flagged = providers.stream()
                .flatMap(provider -> provider.keys().stream())
                .filter(ConfigKey::publishedToFramework)
                .map(ConfigKey::key)
                .collect(Collectors.toSet());
        if (flagged.isEmpty()) {
            return Map.of();
        }

        var dbSourceConfig = DbSourceConfig.loadValues(appName);
        if (!dbSourceConfig.isEnabled() || dbSourceConfig.getUrl() == null) {
            return Map.of();
        }

        return new CachedDbConfigSource(dbSourceConfig).getProperties().entrySet().stream()
                .filter(entry -> flagged.contains(entry.getKey()))
                .filter(entry -> entry.getValue() != null)
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
