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
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Describes, per legacy configuration key, the shape X-Road 8 needs the migrated value stored in.
 * <p>
 * Complements {@link LegacyConfigPathMapping} (key renames, without inspecting values) and
 * {@link LegacyConfigValueRules} (deriving additional keys from a value): this table says what
 * shape a single migrated key's own value must take.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LegacyConfigValueShapes {

    private static final Map<String, ValueShape> SHAPES = new HashMap<>();

    private static final String GROUPING_KEYS_KEY = "xroad.message-log-encryption.archive.grouping-keys";
    private static final Pattern GROUPING_KEYS_ROW_PATTERN =
            Pattern.compile("^" + Pattern.quote(GROUPING_KEYS_KEY) + "\\.\"([^\"]*)\"\\[\\d+]$");
    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder().build();

    static {
        SHAPES.put("xroad.message-log-encryption.archive.grouping-strategy",
                new EnumShape(List.of("NONE", "MEMBER", "SUBSYSTEM")));
        SHAPES.put(GROUPING_KEYS_KEY,
                new MergedJsonShape(LegacyConfigValueShapes::isGroupingKeysRow, LegacyConfigValueShapes::mergeGroupingKeysRows));
    }

    static ValueShape shapeOf(String key) {
        return SHAPES.get(key);
    }

    /**
     * Finds the key a raw row is merged into, if {@code key} is one of a {@link MergedJsonShape}'s own
     * indexed or per-field rows. Complements {@link #shapeOf(String)}'s exact lookup: a merged shape's
     * rows carry its merged key only as a prefix, never as the exact key itself.
     */
    static Optional<String> mergedKeyFor(String key) {
        return SHAPES.entrySet().stream()
                .filter(entry -> entry.getValue() instanceof MergedJsonShape mergedJsonShape && mergedJsonShape.rowMatcher().test(key))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    private static boolean isGroupingKeysRow(String key) {
        return GROUPING_KEYS_ROW_PATTERN.matcher(key).matches();
    }

    private static String mergeGroupingKeysRows(Map<String, String> rows) {
        Map<String, Set<String>> keyIdsByMember = new TreeMap<>();
        rows.forEach((rowKey, keyId) -> {
            var matcher = GROUPING_KEYS_ROW_PATTERN.matcher(rowKey);
            if (matcher.matches()) {
                keyIdsByMember.computeIfAbsent(matcher.group(1), _ -> new TreeSet<>()).add(keyId);
            }
        });
        return JSON_MAPPER.writeValueAsString(keyIdsByMember);
    }

    /**
     * A shape a legacy configuration value is converted into.
     */
    sealed interface ValueShape permits EnumShape, MergedJsonShape {
    }

    /**
     * An enum-valued key, stored as the exact name of one of a fixed set of constants.
     */
    record EnumShape(List<String> allowedConstants) implements ValueShape {
        EnumShape {
            allowedConstants = List.copyOf(allowedConstants);
        }
    }

    /**
     * A key whose value X-Road 8 reads as one JSON object, assembled by merging many legacy rows that
     * share this key as a prefix - one row per indexed entry (archive grouping keys) or per field
     * (signer modules). {@code rowMatcher} recognizes a raw row as one of this shape's own; {@code merger}
     * turns the matching rows into the single JSON value stored under this key.
     */
    record MergedJsonShape(Predicate<String> rowMatcher, Function<Map<String, String>, String> merger) implements ValueShape {
    }

}
