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
package org.niis.xroad.bootstrap;

import ee.ria.xroad.common.SystemPropertySource;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.Config;

@Slf4j
@RequiredArgsConstructor
public class XrdQuarkusApplication implements QuarkusApplication {
    private final Config config;

    void onStart(@Observes StartupEvent ev) {
        log.info("Setting property source to Quarkus config..");
        initializePropertyResolver();
        log.info("Initializing Apache Santuario XML Security library..");
        org.apache.xml.security.Init.init();
    }

    @Override
    public int run(String... args) {
        Quarkus.waitForExit();
        return 0;
    }

    private void initializePropertyResolver() {
        SystemPropertySource.setPropertyResolver(new SystemPropertySource.PropertyResolver() {
            @Override
            public String getProperty(String key) {
                return config.getValue(key, String.class);
            }

            @Override
            public String getProperty(String key, String defaultValue) {
                return config.getOptionalValue(key, String.class)
                        .orElse(defaultValue);
            }

            @Override
            public <T> T getProperty(String key, Class<T> targetType) {
                return config.getValue(key, targetType);
            }
        });
    }
}
