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

import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.source.yaml.YamlConfigSource;
import lombok.experimental.UtilityClass;
import org.eclipse.edc.boot.system.DefaultServiceExtensionContext;
import org.eclipse.edc.boot.system.DependencyGraph;
import org.eclipse.edc.boot.system.ExtensionLoader;
import org.eclipse.edc.boot.system.ServiceLocatorImpl;
import org.eclipse.edc.boot.system.injection.InjectionPointDefaultServiceSupplier;
import org.eclipse.edc.boot.system.injection.InjectorImpl;
import org.eclipse.edc.boot.system.injection.lifecycle.ExtensionLifecycleManager;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.ConsoleMonitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Loads every {@link ServiceExtension} from the current classpath, drops the ones the given
 * {@code application.yaml} excludes together with any extension a loaded {@link ReplacesExtensions} instance
 * declares replaced, and runs EDC's injection point scan on the rest, as the runtime does at boot.
 * The scan reflects over the declared fields of each extension, so a field whose type is missing from the
 * classpath fails here with the same error the runtime would hit.
 */
@UtilityClass
public class ExtensionScanGuard {

    /**
     * Scans the extensions that remain after applying the exclusions of the given {@code application.yaml} and
     * any replacement a loaded {@link ReplacesExtensions} instance declares.
     *
     * @param applicationYaml the application's {@code application.yaml}
     * @return the extensions that were scanned
     */
    public static List<ServiceExtension> scanNonExcluded(URL applicationYaml) {
        return scanNonExcluded(excludedSettingFrom(applicationYaml));
    }

    /**
     * Scans the extensions that remain after applying the given exclusion setting value, in place of whatever
     * {@code application.yaml} would otherwise supply.
     *
     * @param excludedServiceExtensions the value of {@value EdcRuntime#SETTING_EXCLUDED_SERVICE_EXTENSIONS}, or blank
     * @return the extensions that were scanned
     */
    public static List<ServiceExtension> scanNonExcluded(String excludedServiceExtensions) {
        var settings = excludedServiceExtensions == null || excludedServiceExtensions.isBlank()
                ? Map.<String, String>of()
                : Map.of(EdcRuntime.SETTING_EXCLUDED_SERVICE_EXTENSIONS, excludedServiceExtensions);
        var context = new DefaultServiceExtensionContext(new ConsoleMonitor(), ConfigFactory.fromMap(settings));
        context.initialize();

        var loaded = new ExtensionLoader(new ServiceLocatorImpl()).loadExtensions(ServiceExtension.class, true);
        var scanned = EdcRuntime.filterExcluded(loaded, context);
        DependencyGraph.of(context, scanned);
        return scanned;
    }

    private static String excludedSettingFrom(URL applicationYaml) {
        try {
            return new YamlConfigSource(applicationYaml).getValue(EdcRuntime.SETTING_EXCLUDED_SERVICE_EXTENSIONS);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Initializes the extensions that remain after applying the exclusions of the given {@code application.yaml}:
     * runs the inject → {@code initialize()} → provide steps of the runtime's own boot lifecycle ({@link
     * ExtensionLifecycleManager#bootServiceExtensions}), against the full settings the yaml declares plus {@code
     * overrides}, stopping short of the following {@code prepare()}/{@code start()} phases. {@code
     * contextCustomizer} runs against the initialized, but not yet frozen, context before the dependency graph is
     * built, so a caller can register the services the Quarkus CDI bridge would otherwise supply (for example a
     * {@code VaultClient} or a {@code GlobalConfProvider}) — this method builds a bare {@link
     * DefaultServiceExtensionContext}, with none of that bridging.
     *
     * @param applicationYaml the application's {@code application.yaml}
     * @param overrides settings applied on top of the yaml's own, such as a callback address or identity DB settings
     * @param contextCustomizer registers any service the scanned extensions need that the yaml's settings do not supply
     * @return the extensions that were initialized, in dependency order
     * @throws EdcException if the dependency graph is invalid, or an extension throws during {@code initialize()} or
     *         a {@code @Provider} method
     */
    public static List<ServiceExtension> initializeNonExcluded(URL applicationYaml, Map<String, String> overrides,
            Consumer<ServiceExtensionContext> contextCustomizer) {
        var settings = new HashMap<>(settingsFrom(applicationYaml));
        settings.putAll(overrides);
        var context = new DefaultServiceExtensionContext(new ConsoleMonitor(), ConfigFactory.fromMap(settings));
        context.initialize();
        contextCustomizer.accept(context);

        var loaded = new ExtensionLoader(new ServiceLocatorImpl()).loadExtensions(ServiceExtension.class, true);
        var scanned = EdcRuntime.filterExcluded(loaded, context);
        var graph = DependencyGraph.of(context, scanned);
        if (!graph.isValid()) {
            var failures = graph.getInjectionFailures().map(Object::toString).collect(Collectors.joining("; "));
            throw new EdcException("Unsatisfied injection points: " + failures);
        }

        var injector = new InjectorImpl(new InjectionPointDefaultServiceSupplier());
        for (var container : graph.getInjectionContainers()) {
            injector.inject(container, context);
            container.getInjectionTarget().initialize(context);
            var serviceProviders = container.getServiceProviders();
            if (serviceProviders != null) {
                serviceProviders.forEach(serviceProvider -> serviceProvider.get(context));
            }
        }
        return scanned;
    }

    private static Map<String, String> settingsFrom(URL applicationYaml) {
        try {
            var yamlSource = new YamlConfigSource(applicationYaml);
            var config = new SmallRyeConfigBuilder().withSources(yamlSource).addDefaultInterceptors().build();
            var settings = new HashMap<String, String>();
            for (var name : yamlSource.getPropertyNames()) {
                config.getOptionalValue(name, String.class).ifPresent(value -> settings.put(name, value));
            }
            return settings;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
