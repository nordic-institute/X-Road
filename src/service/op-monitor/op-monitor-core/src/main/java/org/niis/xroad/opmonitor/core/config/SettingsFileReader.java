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
package org.niis.xroad.opmonitor.core.config;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.dataformat.yaml.JacksonYAMLParseException;
import tools.jackson.dataformat.yaml.YAMLAnchorReplayingFactory;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLReadFeature;
import tools.jackson.dataformat.yaml.YAMLSchema;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reads the op-monitor settings file: one YAML document of flat dotted keys with plain text values.
 * Values keep the text as written, so the key validators see what the administrator typed.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SettingsFileReader {

    private static final YAMLMapper MAPPER = YAMLMapper.builder(
                    YAMLAnchorReplayingFactory.builder().yamlSchema(YAMLSchema.FAILSAFE).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(YAMLReadFeature.EMPTY_DOCUMENT_AS_EMPTY_OBJECT)
            .build();
    private static final TypeReference<Map<String, String>> RAW_MAP = new TypeReference<>() { };
    private static final Pattern PARSER_MARK =
            Pattern.compile("^ in reader, line (\\d+), column \\d+:$", Pattern.MULTILINE | Pattern.UNIX_LINES);
    private static final Pattern INI_LINE =
            Pattern.compile("^\\s*(\\[[^\\]\\r\\n]+]|[A-Za-z0-9._-]+\\s*=.*)$", Pattern.MULTILINE);
    private static final String INI_HINT = "X-Road 7 ini file? The settings file is YAML with flat "
            + "'dotted.key: value' lines, not [section] headers and key=value lines.";

    /**
     * @param file settings file path
     * @return key to raw value, empty when the file does not exist or holds no settings
     * @throws IllegalArgumentException when the file is not a single flat YAML map of keys to values
     */
    static Map<String, String> read(Path file) {
        if (!Files.exists(file)) {
            return Map.of();
        }
        var text = readText(file);
        Map<String, String> values;
        try {
            values = MAPPER.readValue(text, RAW_MAP);
        } catch (JacksonException e) {
            throw invalid(file, text, lineOf(e), describe(e));
        }
        if (values == null) {
            return Map.of();
        }
        values.forEach((key, value) -> {
            if (value == null) {
                throw new IllegalArgumentException(
                        "Invalid settings file %s: key '%s' has no value".formatted(file, key));
            }
        });
        return values;
    }

    private static int lineOf(JacksonException e) {
        if (e instanceof JacksonYAMLParseException) {
            var line = 0;
            var marks = PARSER_MARK.matcher(e.getOriginalMessage());
            while (marks.find()) {
                line = Integer.parseInt(marks.group(1));
            }
            return line;
        }
        return e.getLocation() == null ? 0 : e.getLocation().getLineNr();
    }

    private static String describe(JacksonException e) {
        if (e instanceof MismatchedInputException mismatch) {
            return mismatch.getPath().isEmpty()
                    ? "expected a map of 'dotted.key: value' lines in a single YAML document"
                    : "expected flat 'dotted.key: value' lines without nested maps or lists";
        }
        return e.getOriginalMessage();
    }

    private static String readText(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read settings file " + file, e);
        }
    }

    private static IllegalArgumentException invalid(Path file, String text, int line, String reason) {
        var location = line < 1 ? "" : " at line " + line;
        var hint = INI_LINE.matcher(text).find() ? "\n" + INI_HINT : "";
        return new IllegalArgumentException("Invalid settings file %s%s: %s%s".formatted(file, location, reason, hint));
    }
}
