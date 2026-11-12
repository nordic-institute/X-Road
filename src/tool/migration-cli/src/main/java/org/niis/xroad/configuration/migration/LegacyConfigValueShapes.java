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
import org.apache.commons.lang3.BooleanUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    private static final String SIGNER_MODULES_KEY = "xroad.signer.modules";
    private static final Pattern SIGNER_MODULE_ROW_PATTERN =
            Pattern.compile("^" + Pattern.quote(SIGNER_MODULES_KEY) + "\\.(.+)\\.([^.]+)$");

    /**
     * Field names and types of {@code SignerModuleConfig} (signer-common). A drift-guard test guards against drift,
     * via a test-only dependency.
     */
    private static final Map<String, FieldType> SIGNER_MODULE_FIELDS = Map.ofEntries(
            Map.entry("enabled", FieldType.BOOLEAN),
            Map.entry("library", FieldType.STRING),
            Map.entry("library-cant-create-os-threads", FieldType.BOOLEAN),
            Map.entry("os-locking-ok", FieldType.BOOLEAN),
            Map.entry("sign-verify-pin", FieldType.BOOLEAN),
            Map.entry("token-id-format", FieldType.STRING),
            Map.entry("sign-mechanism", FieldType.STRING),
            Map.entry("rsa-sign-mechanism", FieldType.STRING),
            Map.entry("ec-sign-mechanism", FieldType.STRING),
            Map.entry("pub-key-attribute-encrypt", FieldType.BOOLEAN),
            Map.entry("pub-key-attribute-verify", FieldType.BOOLEAN),
            Map.entry("pub-key-attribute-wrap", FieldType.BOOLEAN),
            Map.entry("pub-key-attribute-verify-recover", FieldType.BOOLEAN),
            Map.entry("pub-key-attribute-trusted", FieldType.BOOLEAN),
            Map.entry("pub-key-attribute-allowed-mechanisms", FieldType.STRING_LIST),
            Map.entry("priv-key-attribute-sensitive", FieldType.BOOLEAN),
            Map.entry("priv-key-attribute-decrypt", FieldType.BOOLEAN),
            Map.entry("priv-key-attribute-sign", FieldType.BOOLEAN),
            Map.entry("priv-key-attribute-unwrap", FieldType.BOOLEAN),
            Map.entry("priv-key-attribute-sign-recover", FieldType.BOOLEAN),
            Map.entry("priv-key-attribute-extractable", FieldType.BOOLEAN),
            Map.entry("priv-key-attribute-always-sensitive", FieldType.BOOLEAN),
            Map.entry("priv-key-attribute-never-extractable", FieldType.BOOLEAN),
            Map.entry("priv-key-attribute-wrap-with-trusted", FieldType.BOOLEAN),
            Map.entry("priv-key-attribute-allowed-mechanisms", FieldType.STRING_LIST),
            Map.entry("slot-ids", FieldType.SLOT_IDS),
            Map.entry("batch-signing-enabled", FieldType.BOOLEAN),
            Map.entry("read-only", FieldType.BOOLEAN));

    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder().build();

    static {
        SHAPES.put("xroad.message-log-encryption.archive.grouping-strategy",
                new EnumShape(List.of("NONE", "MEMBER", "SUBSYSTEM")));
        SHAPES.put(GROUPING_KEYS_KEY,
                new MergedJsonShape(LegacyConfigValueShapes::isGroupingKeysRow, (rows, _) -> mergeGroupingKeysRows(rows)));
        SHAPES.put(SIGNER_MODULES_KEY,
                new MergedJsonShape(LegacyConfigValueShapes::isSignerModuleRow, LegacyConfigValueShapes::mergeSignerModuleRows));
        SHAPES.put("xroad.proxy-ui-api.signing-key-algorithm", new EnumShape(List.of("RSA", "EC")));
        SHAPES.put("xroad.proxy-ui-api.authentication-key-algorithm", new EnumShape(List.of("RSA", "EC")));
        SHAPES.put("xroad.admin-service.internal-key-algorithm", new EnumShape(List.of("RSA", "EC")));
        SHAPES.put("xroad.admin-service.external-key-algorithm", new EnumShape(List.of("RSA", "EC")));
        SHAPES.put("xroad.signer.ocsp-prioritization-strategy",
                new EnumShape(List.of("ONLY_FREE", "ONLY_PAID", "FREE_FIRST", "PAID_FIRST", "NONE")));
        SHAPES.put("xroad.proxy.message-log.timestamping-prioritization-strategy",
                new EnumShape(List.of("ONLY_FREE", "ONLY_PAID", "FREE_FIRST", "PAID_FIRST", "NONE")));
    }

    static ValueShape shapeOf(String key) {
        return SHAPES.get(key);
    }

    /**
     * {@code SIGNER_MODULE_FIELDS}, for the drift-guard test.
     */
    static Map<String, FieldType> signerModuleFields() {
        return SIGNER_MODULE_FIELDS;
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

    private static boolean isSignerModuleRow(String key) {
        return SIGNER_MODULE_ROW_PATTERN.matcher(key).matches();
    }

    private static String mergeSignerModuleRows(Map<String, String> rows, List<String> warnings) {
        Map<String, Map<String, Object>> fieldsByDevice = new TreeMap<>();
        rows.forEach((rowKey, rawValue) -> {
            var matcher = SIGNER_MODULE_ROW_PATTERN.matcher(rowKey);
            if (matcher.matches()) {
                putSignerModuleField(fieldsByDevice, matcher.group(1), matcher.group(2), rawValue, warnings);
            }
        });
        return JSON_MAPPER.writeValueAsString(fieldsByDevice);
    }

    private static void putSignerModuleField(Map<String, Map<String, Object>> fieldsByDevice, String device, String field,
                                              String rawValue, List<String> warnings) {
        FieldType fieldType = SIGNER_MODULE_FIELDS.get(field);
        if (fieldType == null) {
            warnings.add("Unknown signer module field '%s' for device '%s'; leaving it out".formatted(field, device));
            return;
        }
        convertSignerModuleValue(fieldType, rawValue).ifPresentOrElse(
                converted -> fieldsByDevice.computeIfAbsent(device, _ -> new TreeMap<>()).put(field, converted),
                () -> warnings.add(
                        "Value '%s' for signer module field '%s' of device '%s' cannot be converted to its %s type; leaving it out"
                                .formatted(rawValue, field, device, fieldType)));
    }

    private static Optional<Object> convertSignerModuleValue(FieldType fieldType, String rawValue) {
        return switch (fieldType) {
            case BOOLEAN -> parseBoolean(rawValue);
            case STRING -> Optional.of(rawValue);
            case STRING_LIST -> Optional.of(splitList(rawValue));
            case SLOT_IDS -> parseSlotIds(rawValue);
        };
    }

    private static Optional<Object> parseBoolean(String rawValue) {
        return Optional.<Object>ofNullable(BooleanUtils.toBooleanObject(rawValue));
    }

    private static List<String> splitList(String rawValue) {
        return Arrays.stream(rawValue.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .toList();
    }

    private static Optional<Object> parseSlotIds(String rawValue) {
        try {
            Set<Long> slotIds = Arrays.stream(rawValue.split(","))
                    .map(String::trim)
                    .filter(entry -> !entry.isEmpty())
                    .map(Long::parseLong)
                    .collect(Collectors.toCollection(TreeSet::new));
            return Optional.of(slotIds);
        } catch (NumberFormatException _) {
            return Optional.empty();
        }
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
     * turns the matching rows into the single JSON value stored under this key, adding a warning to its
     * second argument for each row it leaves out.
     */
    record MergedJsonShape(Predicate<String> rowMatcher, BiFunction<Map<String, String>, List<String>, String> merger)
            implements ValueShape {
    }

    /**
     * The type a signer module field's legacy string value is converted to.
     */
    enum FieldType {
        BOOLEAN, STRING, STRING_LIST, SLOT_IDS
    }

}
