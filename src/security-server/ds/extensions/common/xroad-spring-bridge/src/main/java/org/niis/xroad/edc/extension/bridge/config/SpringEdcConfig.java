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
package org.niis.xroad.edc.extension.bridge.config;

import lombok.RequiredArgsConstructor;
import org.eclipse.edc.spi.system.configuration.Config;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@RequiredArgsConstructor
public class SpringEdcConfig implements Config {
    private final ConfigurableEnvironment environment;

    @Override
    public String getString(String key) {
        return environment.getProperty(key);
    }

    @Override
    public String getString(String key, String defaultValue) {
        return environment.getProperty(key, defaultValue);
    }

    @Override
    public Integer getInteger(String key) {
        return environment.getProperty(key, Integer.class);
    }

    @Override
    public Integer getInteger(String key, Integer defaultValue) {
        return Optional.ofNullable(environment.getProperty(key, Integer.class)).orElse(defaultValue);
    }

    @Override
    public Long getLong(String key) {
        return environment.getProperty(key, Long.class);
    }

    @Override
    public Long getLong(String key, Long defaultValue) {
        return Optional.ofNullable(environment.getProperty(key, Long.class)).orElse(defaultValue);
    }

    @Override
    public Boolean getBoolean(String key) {
        return environment.getProperty(key, Boolean.class);
    }

    @Override
    public Boolean getBoolean(String key, Boolean defaultValue) {
        return Optional.ofNullable(environment.getProperty(key, Boolean.class)).orElse(defaultValue);
    }

    @Override
    public Config getConfig(String path) {
        return new SpringEdcConfig(environment); // Assuming a new config for the same environment
    }

    @Override
    public Config merge(Config other) {
        // Implement merging logic if needed
        return this; // Placeholder
    }

    @Override
    public Stream<Config> partition() {
        return Stream.of(this); // Return a stream containing this config
    }

    @Override
    public Map<String, String> getEntries() {
        Map<String, String> entries = new HashMap<>();

        //TODO dummy value, EDC expects something to be present, but we do not want expose everything
        entries.put("placeholderKey", "placeholderValue");
        return entries;
    }

    @Override
    public Map<String, String> getRelativeEntries() {
        return getEntries(); // Placeholder for relative entries
    }

    @Override
    public Map<String, String> getRelativeEntries(String basePath) {
        return getEntries(); // Placeholder for relative entries based on basePath
    }

    @Override
    public String currentNode() {
        return ""; // Placeholder for current node
    }

    @Override
    public boolean isLeaf() {
        return true; // Placeholder for leaf check
    }

    @Override
    public boolean hasKey(String key) {
        return environment.containsProperty(key);
    }

    @Override
    public boolean hasPath(String path) {
        return false; // Placeholder for path check
    }
}
