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

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Resolves the active country profile from the environment and loads its bundled file
 * ({@code xroad-config/country-profile/<CC>.yaml}). The {@code xroad} subtree is flattened to dotted DSL keys
 * with string values; lists render as comma-separated strings. The {@code metadata} block is informational
 * and not loaded. The selector is case-insensitive and validated against {@link #SUPPORTED_COUNTRIES}.
 */
@Slf4j
@UtilityClass
public class CountryProfileLoader {

    /** Environment variable that selects the country profile. */
    public static final String PROFILE_ENV_VARIABLE = "XROAD_COUNTRY_PROFILE";
    /** Environment variable listing comma-separated DSL keys to remove from the selected profile. */
    public static final String EXCLUDE_ENV_VARIABLE = "XROAD_COUNTRY_PROFILE_EXCLUDE";
    /** Country codes that have a bundled profile. */
    public static final List<String> SUPPORTED_COUNTRIES = List.of("EE", "FI", "FO", "IS");

    private static final String RESOURCE_DIRECTORY = "xroad-config/country-profile";
    private static final String RESOURCE_SUFFIX = ".yaml";
    private static final String ROOT_KEY = "xroad";
    private static final YAMLMapper MAPPER = YAMLMapper.builder().build();
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    /**
     * @param env environment variables; {@link #PROFILE_ENV_VARIABLE} selects the profile and
     *            {@link #EXCLUDE_ENV_VARIABLE} lists keys to remove from it
     * @return the selected profile, empty when the selector is unset or blank
     * @throws IllegalArgumentException when the selector is not one of {@link #SUPPORTED_COUNTRIES}
     */
    public static Optional<ActiveCountryProfile> resolve(Map<String, String> env) {
        var selector = env.get(PROFILE_ENV_VARIABLE);
        var excludes = parseExcludeList(env.get(EXCLUDE_ENV_VARIABLE));
        if (selector == null || selector.isBlank()) {
            if (!excludes.isEmpty()) {
                log.warn("{} is set but {} selects no country profile; the exclude list is ignored",
                        EXCLUDE_ENV_VARIABLE, PROFILE_ENV_VARIABLE);
            }
            return Optional.empty();
        }
        var code = selector.strip().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_COUNTRIES.contains(code)) {
            throw new IllegalArgumentException("Unknown country profile '%s' in %s; supported profiles: %s"
                    .formatted(code, PROFILE_ENV_VARIABLE, String.join(", ", SUPPORTED_COUNTRIES)));
        }
        var values = readProfile(code);
        applyExcludes(code, values, excludes);
        if (LOGGED.add(code + ":" + values.size())) {
            log.info("Country profile {} active, {} key(s) applied", code, values.size());
        }
        return Optional.of(new ActiveCountryProfile(code, Map.copyOf(values)));
    }

    private static void applyExcludes(String code, Map<String, String> values, Set<String> excludes) {
        for (var entry : excludes) {
            if (values.remove(entry) == null) {
                log.warn("Exclude entry '{}' in {} matches no key of country profile {}",
                        entry, EXCLUDE_ENV_VARIABLE, code);
            }
        }
    }

    private static Set<String> parseExcludeList(String excludeList) {
        if (excludeList == null) {
            return Set.of();
        }
        return Arrays.stream(excludeList.split(","))
                .map(String::strip)
                .filter(entry -> !entry.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Map<String, String> readProfile(String code) {
        var resource = RESOURCE_DIRECTORY + "/" + code + RESOURCE_SUFFIX;
        try (var in = CountryProfileLoader.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Bundled country profile %s is missing: %s".formatted(code, resource));
            }
            return parse(code, in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read country profile " + code, e);
        }
    }

    static Map<String, String> parse(String profile, InputStream in) {
        try {
            var values = new LinkedHashMap<String, String>();
            flatten(profile, ROOT_KEY, MAPPER.readTree(in).path(ROOT_KEY), values);
            return values;
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to read country profile " + profile, e);
        }
    }

    private static void flatten(String profile, String prefix, JsonNode node, Map<String, String> out) {
        if (node.isObject()) {
            node.properties().forEach(entry ->
                    flatten(profile, prefix + "." + entry.getKey(), entry.getValue(), out));
        } else if (node.isArray()) {
            out.put(prefix, node.valueStream()
                    .map(item -> plainValue(profile, prefix, item))
                    .collect(Collectors.joining(",")));
        } else if (!node.isMissingNode()) {
            out.put(prefix, plainValue(profile, prefix, node));
        }
    }

    private static String plainValue(String profile, String key, JsonNode node) {
        if (node.isNull() || node.isContainer()) {
            throw new IllegalStateException(
                    "Invalid country profile %s: key %s must hold a plain value or a list of plain values"
                            .formatted(profile, key));
        }
        return node.asString();
    }
}
