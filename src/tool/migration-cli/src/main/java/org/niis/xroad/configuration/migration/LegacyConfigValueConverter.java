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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Converts legacy configuration values into the exact shape X-Road 8 reads, as described by
 * {@link LegacyConfigValueShapes}.
 * <p>
 * Pure function: raw rows in, converted rows and warnings out. Performs no database access.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LegacyConfigValueConverter {

    public static ConversionResult convert(Map<String, String> rawProperties) {
        Map<String, String> merged = mergeStructuredRows(rawProperties);

        Map<String, String> converted = new TreeMap<>();
        List<String> warnings = new ArrayList<>();

        merged.forEach((key, value) -> converted.put(key, convertValue(key, value, warnings)));

        return new ConversionResult(converted, warnings);
    }

    /**
     * Replaces every {@link LegacyConfigValueShapes.MergedJsonShape}'s own rows (indexed entries or
     * per-field rows, found via its key's prefix-aware lookup) with the single merged key holding the
     * JSON value built from them. Rows belonging to no such shape pass through unchanged.
     */
    private static Map<String, String> mergeStructuredRows(Map<String, String> rawProperties) {
        Map<String, Map<String, String>> rowsByMergedKey = new TreeMap<>();
        Map<String, String> passthrough = new TreeMap<>();

        rawProperties.forEach((key, value) -> {
            Optional<String> mergedKey = LegacyConfigValueShapes.mergedKeyFor(key);
            if (mergedKey.isPresent()) {
                rowsByMergedKey.computeIfAbsent(mergedKey.get(), _ -> new TreeMap<>()).put(key, value);
            } else {
                passthrough.put(key, value);
            }
        });

        Map<String, String> result = new TreeMap<>(passthrough);
        rowsByMergedKey.forEach((mergedKey, rows) -> {
            var shape = (LegacyConfigValueShapes.MergedJsonShape) LegacyConfigValueShapes.shapeOf(mergedKey);
            result.put(mergedKey, shape.merger().apply(rows));
        });

        return result;
    }

    private static String convertValue(String key, String value, List<String> warnings) {
        if (LegacyConfigValueShapes.shapeOf(key) instanceof LegacyConfigValueShapes.EnumShape enumShape) {
            return convertEnum(key, value, enumShape, warnings);
        }
        return value;
    }

    private static String convertEnum(String key, String value, LegacyConfigValueShapes.EnumShape enumShape,
                                       List<String> warnings) {
        return enumShape.allowedConstants().stream()
                .filter(constant -> constant.equalsIgnoreCase(value))
                .findFirst()
                .orElseGet(() -> {
                    warnings.add("Value '%s' for key '%s' matches none of the allowed values %s; keeping it unchanged"
                            .formatted(value, key, enumShape.allowedConstants()));
                    return value;
                });
    }

    public record ConversionResult(Map<String, String> properties, List<String> warnings) {
        public ConversionResult {
            properties = Map.copyOf(properties);
            warnings = List.copyOf(warnings);
        }
    }

}
