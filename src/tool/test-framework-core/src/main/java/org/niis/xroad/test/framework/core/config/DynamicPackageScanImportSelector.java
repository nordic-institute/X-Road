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
package org.niis.xroad.test.framework.core.config;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Import selector that dynamically imports @Configuration classes from packages
 * specified in test-automation.yaml configuration.
 */
@Slf4j
public class DynamicPackageScanImportSelector implements ImportSelector {

    @Nonnull
    @Override
    public String[] selectImports(@Nonnull AnnotationMetadata importingClassMetadata) {
        var properties = TestFrameworkConfigSource.getInstance().getCoreProperties();

        if (properties == null
                || properties.componentScan() == null
                || properties.componentScan().additionalPackages().isEmpty()) {
            return new String[0];
        }

        List<String> additionalPackages = properties.componentScan().additionalPackages().get();
        if (additionalPackages.isEmpty()) {
            return new String[0];
        }

        log.info("Scanning additional packages for configuration classes: {}", additionalPackages);

        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        // Scan for @Configuration and @Component (which includes @Service, @Repository, etc.)
        scanner.addIncludeFilter(new AnnotationTypeFilter(org.springframework.context.annotation.Configuration.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));

        List<String> configurations = new ArrayList<>();
        for (String packageName : additionalPackages) {
            var candidates = scanner.findCandidateComponents(packageName);
            for (var candidate : candidates) {
                String className = candidate.getBeanClassName();
                if (className != null) {
                    configurations.add(className);
                    log.debug("Found configuration/component class: {}", className);
                }
            }
        }

        log.info("Importing {} configuration/component classes from additional packages", configurations.size());
        return configurations.toArray(new String[0]);
    }
}
