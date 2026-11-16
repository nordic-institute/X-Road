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
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Loads a bundled country profile ({@code xroad-config/country-profile/<CC>.yaml}) and flattens
 * its {@code xroad} subtree to dotted DSL keys with string values. Lists render as
 * comma-separated strings. The {@code metadata} block is informational and not loaded.
 */
@Slf4j
@UtilityClass
public class CountryProfileLoader {

    /** Environment variable that selects the country profile. */
    public static final String PROFILE_ENV_VARIABLE = "XROAD_COUNTRY_PROFILE";

    private static final String RESOURCE_PATTERN = "xroad-config/country-profile/%s.yaml";
    private static final String ROOT_KEY = "xroad";

    private static final YAMLMapper MAPPER = YAMLMapper.builder().build();

    /**
     * @param profileName country profile name, case-insensitive; {@code null} or blank means no profile
     * @return DSL key to string value, empty when no profile is selected
     */
    public static Map<String, String> load(String profileName) {
        if (profileName == null || profileName.isBlank()) {
            return Map.of();
        }
        var name = profileName.strip().toUpperCase(Locale.ROOT);
        var values = readProfile(name);
        log.info("Country profile {} active, {} key(s) applied", name, values.size());
        return values;
    }

    private static Map<String, String> readProfile(String name) {
        var resource = RESOURCE_PATTERN.formatted(name);
        try (var in = CountryProfileLoader.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalArgumentException("Unknown country profile: " + name);
            }
            var values = new LinkedHashMap<String, String>();
            flatten(ROOT_KEY, MAPPER.readTree(in).path(ROOT_KEY), values);
            return Map.copyOf(values);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read country profile " + name, e);
        }
    }

    private static void flatten(String prefix, JsonNode node, Map<String, String> out) {
        if (node.isObject()) {
            node.properties().forEach(entry -> flatten(prefix + "." + entry.getKey(), entry.getValue(), out));
        } else if (node.isArray()) {
            out.put(prefix, StreamSupport.stream(node.spliterator(), false)
                    .map(JsonNode::asString)
                    .collect(Collectors.joining(",")));
        } else if (!node.isMissingNode() && !node.isNull()) {
            out.put(prefix, node.asString());
        }
    }
}
