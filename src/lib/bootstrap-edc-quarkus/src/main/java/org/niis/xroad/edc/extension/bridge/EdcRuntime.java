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
package org.niis.xroad.edc.extension.bridge;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.edc.boot.system.DependencyGraph;
import org.eclipse.edc.boot.system.ExtensionLoader;
import org.eclipse.edc.boot.system.ServiceLocatorImpl;
import org.eclipse.edc.boot.system.runtime.BaseRuntime;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.Config;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * EDC runtime implementation for Quarkus environment. Handles EDC lifecycle within Quarkus.
 */
@Slf4j
@Startup
@ApplicationScoped
@RequiredArgsConstructor
public class EdcRuntime extends BaseRuntime {

    static final String SETTING_EXCLUDED_SERVICE_EXTENSIONS = "xroad.edc.boot.excluded-service-extensions";

    private final QuarkusLoggingBridge loggingBridge;
    private final QuarkusConfigBridge configBridge;
    private final ExtensionLoader extensionLoader = new ExtensionLoader(new ServiceLocatorImpl());

    @PreDestroy
    @Override
    public void shutdown() {
        super.shutdown();
    }

    @PostConstruct
    public void afterPropertiesSet() {
        var startupTime = System.currentTimeMillis();
        log.info("Loading EDC runtime..");
        try {
            boot(false);
        } catch (Exception e) {
            log.error("Failed to boot EDC runtime", e);
            throw e;
        }
        log.info("EDC runtime loaded in {} ms", System.currentTimeMillis() - startupTime);
    }

    @Override
    protected @NotNull ServiceExtensionContext createContext(Monitor monitor, Config config) {
        return new QuarkusServiceExtensionContext(loggingBridge, configBridge);
    }

    /**
     * Loads {@link ServiceExtension}s the same way {@link BaseRuntime} does, except extensions whose class name is
     * listed under {@value SETTING_EXCLUDED_SERVICE_EXTENSIONS} (comma-separated, fully qualified) are dropped
     * before the dependency graph is built. This lets an X-Road-side replacement extension supersede an upstream
     * one without resorting to classpath surgery.
     */
    @Override
    protected DependencyGraph buildDependencyGraph(ServiceExtensionContext context) {
        var extensions = filterExcluded(extensionLoader.loadExtensions(ServiceExtension.class, true), context);
        return DependencyGraph.of(context, extensions);
    }

    static List<ServiceExtension> filterExcluded(List<ServiceExtension> extensions, ServiceExtensionContext context) {
        var excluded = excludedServiceExtensionTypes(context);
        if (excluded.isEmpty()) {
            return extensions;
        }
        return extensions.stream()
                .filter(extension -> !excluded.contains(extension.getClass().getName()))
                .toList();
    }

    private static Set<String> excludedServiceExtensionTypes(ServiceExtensionContext context) {
        var value = context.getSetting(SETTING_EXCLUDED_SERVICE_EXTENSIONS, "");
        if (value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(type -> !type.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
