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

import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.niis.xroad.common.properties.config.ConfigKeyProvider;
import org.niis.xroad.common.properties.config.FrameworkPublishedConfig;
import org.niis.xroad.common.properties.config.keys.ConfigKeyProviders;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Registers {@link XRoadDefaultsConfigSource} with the packaged catalogue
 * ({@link ConfigKeyProviders#allProviders()}), plus {@link XRoadFrameworkConfigSource} for the stored
 * overrides of keys marked {@link org.niis.xroad.common.properties.config.ConfigKey#publishedToFramework()}.
 * Wired via {@code META-INF/services/io.smallrye.config.ConfigSourceFactory}.
 *
 * <p>An app may narrow what is published by setting {@code xroad.config.defaults-scopes} to a
 * comma-separated list of scope root paths (e.g. {@code xroad.common}); only those providers'
 * defaults are then published. When unset, every provider's defaults are published. Narrowing
 * keeps the source from leaking defaults into an app that maps a prefix only partially with a
 * {@code @ConfigMapping} — SmallRye would otherwise reject the extra keys as unknown.
 */
@Slf4j
public final class XRoadDefaultsConfigSourceFactory implements ConfigSourceFactory {

    static final String DEFAULTS_SCOPES_PROPERTY = "xroad.config.defaults-scopes";

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context) {
        var configured = context.getValue(DEFAULTS_SCOPES_PROPERTY);
        var scopes = configured == null ? null : configured.getValue();
        var providers = selectProviders(ConfigKeyProviders.allProviders(), scopes);
        var appName = context.getValue("quarkus.application.name");
        var storedOverrides = FrameworkPublishedConfig.storedOverrides(
                appName == null ? null : appName.getValue(), providers);
        if (storedOverrides.isEmpty()) {
            return List.of(new XRoadDefaultsConfigSource(providers));
        }
        log.info("Publishing {} stored override(s) of framework-visible keys: {}",
                storedOverrides.size(), storedOverrides.keySet());
        return List.of(new XRoadDefaultsConfigSource(providers), new XRoadFrameworkConfigSource(storedOverrides));
    }

    static List<ConfigKeyProvider> selectProviders(List<ConfigKeyProvider> providers, String scopesCsv) {
        if (scopesCsv == null || scopesCsv.isBlank()) {
            return providers;
        }
        Set<String> allowed = Arrays.stream(scopesCsv.split(","))
                .map(String::trim)
                .filter(scope -> !scope.isEmpty())
                .collect(Collectors.toSet());
        return providers.stream()
                .filter(provider -> allowed.contains(provider.scope().rootPath()))
                .toList();
    }
}
