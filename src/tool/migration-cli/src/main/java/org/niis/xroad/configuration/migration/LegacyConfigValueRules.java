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

package org.niis.xroad.configuration.migration;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Derives additional properties based on the value of a source legacy key.
 * <p>
 * Complements {@link LegacyConfigPathMapping} (which does key → keys mapping without
 * inspecting values). Rules of the shape "if key X has value matching predicate P,
 * also emit entries E" are registered as a single static list and evaluated by
 * {@link #derive(String, String)}.
 * <p>
 * Adding a new rule is a one-line addition to {@link #RULES}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LegacyConfigValueRules {

    private static final List<ValueRule> RULES = List.of(
            // proxy.health-check-port implied that health-check was enabled when the port was non-zero.
            // The new config introduces an explicit opt-in flag; preserve behavior on migration.
            new ValueRule(
                    "proxy.health-check-port",
                    value -> value != null && !value.trim().isEmpty() && !"0".equals(value.trim()),
                    Map.of("proxy.health-check-enabled", Boolean.TRUE))
    );

    /**
     * Returns properties derived from a source key's raw string value.
     * <p>
     * Source keys use the legacy path (e.g. {@code "proxy.health-check-port"}), WITHOUT the
     * downstream {@code "xroad."} prefix. The caller is responsible for adding that prefix
     * (and, for YAML, nesting) when merging the result into its output.
     *
     * @param sourceKey legacy key (e.g. {@code "proxy.health-check-port"})
     * @param rawValue  raw string value as read from the source file (may be {@code null})
     * @return immutable map of derived legacy-shaped keys to typed values;
     * empty if no rule matches
     */
    public static Map<String, Object> derive(String sourceKey, String rawValue) {
        Map<String, Object> result = new HashMap<>();
        for (ValueRule rule : RULES) {
            if (rule.sourceKey().equals(sourceKey) && rule.condition().test(rawValue)) {
                result.putAll(rule.derived());
            }
        }
        return Map.copyOf(result);
    }

    private record ValueRule(String sourceKey, Predicate<String> condition, Map<String, Object> derived) {
    }

}
