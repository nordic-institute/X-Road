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

import java.util.List;
import java.util.Optional;

/**
 * Read-only projection of declared {@link ConfigKey}s into a flat catalogue. Backs the
 * admin-service "all properties" view without reading any YAML: the definitions, defaults,
 * and constraints come from the registered {@link ConfigKeyProvider}s.
 */
public final class ConfigCatalogue {

    private ConfigCatalogue() {
    }

    /**
     * One declared property as seen by the catalogue: its effective key, scope grouping label,
     * value type, declared default (raw), and validation summary.
     *
     * @param key               effective key, e.g. {@code xroad.signer.key-length}
     * @param scope             scope grouping label, empty for scope-less globals
     * @param type              declared value type
     * @param defaultValue      raw declared default, {@code null} when none is declared
     * @param validationSummary human-readable constraint, empty when unconstrained
     */
    public record Entry(String key,
                        Optional<String> scope,
                        Class<?> type,
                        String defaultValue,
                        Optional<String> validationSummary) {

        /**
         * @param key declared key to project
         * @return catalogue entry for {@code key}
         */
        public static Entry of(ConfigKey<?> key) {
            return new Entry(key.key(), key.scopeName(), key.type(), key.defaultValue(), key.validationSummary());
        }
    }

    /**
     * @param providers providers whose declared keys make up the catalogue
     * @return every declared key across {@code providers}, projected to an {@link Entry}
     */
    public static List<Entry> from(List<ConfigKeyProvider> providers) {
        return providers.stream()
                .flatMap(provider -> provider.keys().stream())
                .map(Entry::of)
                .toList();
    }
}
