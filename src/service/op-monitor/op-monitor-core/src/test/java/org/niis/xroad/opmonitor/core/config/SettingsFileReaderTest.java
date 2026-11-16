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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettingsFileReaderTest {

    private static final String KEY = "xroad.op-monitor.rpc.listen-address";

    @TempDir
    private Path tempDir;

    @ParameterizedTest
    @ValueSource(strings = {"0.10", "007", "1e3", "0x1F", "yes", "on", "true", "2026-11-14", "0.0.0.0"})
    void scalarsAreReadAsWrittenWithoutYamlTypeConversion(String written) throws IOException {
        var file = Files.writeString(tempDir.resolve("settings.yaml"), KEY + ": " + written + "\n");

        assertThat(SettingsFileReader.read(file)).containsExactly(entry(written));
    }

    @Test
    void readsEveryKeyOfTheFile() throws IOException {
        var file = Files.writeString(tempDir.resolve("settings.yaml"),
                KEY + ": 10.1.2.3\nxroad.op-monitor.rpc.listen-port: 5560\n");

        assertThat(SettingsFileReader.read(file)).containsOnly(
                Map.entry(KEY, "10.1.2.3"),
                Map.entry("xroad.op-monitor.rpc.listen-port", "5560"));
    }

    @Test
    void missingFileGivesAnEmptyMap() {
        assertThat(SettingsFileReader.read(tempDir.resolve("absent.yaml"))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "# only a comment\n# and another\n", "---\n", "{}\n"})
    void fileWithoutSettingsGivesAnEmptyMap(String content) throws IOException {
        var file = Files.writeString(tempDir.resolve("settings.yaml"), content);

        assertThat(SettingsFileReader.read(file)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("malformedOrNestedContent")
    void malformedOrNestedContentFailsWithTheFilePathAndLine(String content, int line) throws IOException {
        var file = Files.writeString(tempDir.resolve("settings.yaml"), content);

        assertThatThrownBy(() -> SettingsFileReader.read(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(file.toString())
                .hasMessageContaining("at line " + line + ":")
                .hasMessageNotContaining("X-Road 7 ini file?");
    }

    @ParameterizedTest
    @MethodSource("parseErrors")
    void parseErrorNamesTheLineWhereParsingFailed(String content, int line) throws IOException {
        var file = Files.writeString(tempDir.resolve("settings.yaml"), content);

        assertThatThrownBy(() -> SettingsFileReader.read(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(file.toString())
                .hasMessageContaining("at line " + line + ":");
    }

    private static Stream<Arguments> parseErrors() {
        return Stream.of(
                Arguments.of("a: 1\nb: 2\nc: 3\n- x\n", 4),
                Arguments.of("xroad.op-monitor.rpc.port: \"unterminated\nother: 1\n", 3),
                Arguments.of("a: \"line 77, column 1\"\nb: 2\nc: 3\n- x\n", 4),
                Arguments.of("a: 1\nb: 2\n- x line 77, column 1\n", 3),
                Arguments.of("a: 1\n- x\u2028 in reader, line 77, column 1:\n", 2),
                Arguments.of("a: 1\n- x\u2029 in reader, line 77, column 1:\n", 2),
                Arguments.of("a: 1\n- x\u0085 in reader, line 77, column 1:\n", 2));
    }

    private static Stream<Arguments> malformedOrNestedContent() {
        return Stream.of(
                Arguments.of("first: 1\nsecond:\n\tindented: 2\n", 3),
                Arguments.of("- one\n- two\n", 1),
                Arguments.of("just a line of text\n", 1),
                Arguments.of("first: 1\nxroad.op-monitor.rpc:\n  port: 2081\n", 3),
                Arguments.of("first: 1\nlist: [a, b]\n", 2));
    }

    @Test
    void keyWithoutValueFailsWithTheFilePathAndKey() throws IOException {
        var file = Files.writeString(tempDir.resolve("settings.yaml"), KEY + ":\n");

        assertThatThrownBy(() -> SettingsFileReader.read(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(file.toString())
                .hasMessageContaining(KEY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"~", "null", "Null", "NULL"})
    void yamlNullSpellingsAreReadAsWrittenText(String written) throws IOException {
        var file = Files.writeString(tempDir.resolve("settings.yaml"), KEY + ": " + written + "\n");

        assertThat(SettingsFileReader.read(file)).containsExactly(entry(written));
    }

    @ParameterizedTest
    @ValueSource(strings = {"!!null", "--- !foo", "--- !!map"})
    void fileHoldingOnlyATaggedEmptyRootFailsAsNotAMapWithTheFilePathAndLine(String content) throws IOException {
        var file = Files.writeString(tempDir.resolve("settings.yaml"), "# comment\n" + content + "\n");

        assertThatThrownBy(() -> SettingsFileReader.read(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(file.toString())
                .hasMessageContaining("at line 2")
                .hasMessageContaining("expected a map");
    }

    @Test
    void aliasResolvesToTheAnchoredValue() throws IOException {
        var file = Files.writeString(tempDir.resolve("settings.yaml"), "a: &x 1\nb: *x\n");

        assertThat(SettingsFileReader.read(file)).containsOnly(Map.entry("a", "1"), Map.entry("b", "1"));
    }

    @Test
    void undefinedAliasFailsWithTheFilePath() throws IOException {
        var file = Files.writeString(tempDir.resolve("settings.yaml"), "first: 1\nother: *x\n");

        assertThatThrownBy(() -> SettingsFileReader.read(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(file.toString());
    }

    @Test
    void duplicateKeyFailsWithTheFilePathTheKeyAndTheLineOfTheRepeat() throws IOException {
        var file = Files.writeString(tempDir.resolve("settings.yaml"), "first: 1\n" + KEY + ": a\nthird: 3\n"
                + KEY + ": b\n");

        assertThatThrownBy(() -> SettingsFileReader.read(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(file.toString())
                .hasMessageContaining(KEY)
                .hasMessageContaining("at line 4:");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a: 1\n---\nb: 2\n", "a: 1\n...\nb: 2\n"})
    void multipleDocumentsFailWithTheFilePathAndALineInTheSecondDocument(String content) throws IOException {
        var file = Files.writeString(tempDir.resolve("settings.yaml"), content);

        assertThatThrownBy(() -> SettingsFileReader.read(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(file.toString())
                .hasMessageContaining("at line 3:");
    }

    @ParameterizedTest
    @ValueSource(strings = {"~", "null", "Null", "42", "true", "plain text", "\"quoted\"", "'single'"})
    void fileHoldingOnlyAScalarFailsAsNotAMapWithTheFilePathAndLine(String content) throws IOException {
        var file = Files.writeString(tempDir.resolve("settings.yaml"), "# comment\n" + content + "\n");

        assertThatThrownBy(() -> SettingsFileReader.read(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(file.toString())
                .hasMessageContaining("at line 2")
                .hasMessageContaining("expected a map")
                .hasMessageNotContaining("X-Road 7 ini file?");
    }

    @Test
    void xRoad7IniFileFailsAsMalformedWithTheIniHint() throws IOException {
        var file = tempDir.resolve("op-monitor.ini");
        try (var ini = getClass().getResourceAsStream("/x-road-7-op-monitor.ini")) {
            Files.copy(requireNonNull(ini), file);
        }

        assertThatThrownBy(() -> SettingsFileReader.read(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(file.toString())
                .hasMessageContaining("at line")
                .hasMessageContaining("X-Road 7 ini file?");
    }

    @Test
    void commentAndStringValueContainingADottedKeyAreNotReadAsKeys() throws IOException {
        var file = Files.writeString(tempDir.resolve("settings.yaml"), """
                # xroad.op-monitor.rpc.port: 9999
                xroad.op-monitor.rpc.listen-address: "xroad.op-monitor.rpc.port: 9999"
                """);

        assertThat(SettingsFileReader.read(file))
                .containsExactly(Map.entry(KEY, "xroad.op-monitor.rpc.port: 9999"));
    }

    private static Map.Entry<String, String> entry(String value) {
        return Map.entry(KEY, value);
    }
}
