/*
 * The MIT License
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

package org.niis.xroad.migration.tokenpin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PinSourceLookupTest {

    @TempDir
    Path tempDir;

    private final PinSourceLookup lookup = new PinSourceLookup(new AutoLoginScriptExecutor());

    private Path explicitScript;
    private Path customScript;
    private Path defaultScript;
    private Path pinFile;

    @BeforeEach
    void setUp() {
        explicitScript = tempDir.resolve("explicit.sh");
        customScript = tempDir.resolve("custom.sh");
        defaultScript = tempDir.resolve("default.sh");
        pinFile = tempDir.resolve("autologin");
    }

    @Test
    void shouldPreferTheExplicitScriptOverTheCustomScript() throws IOException {
        script(explicitScript, "explicit");
        script(customScript, "custom");
        script(defaultScript, "default");
        Files.writeString(pinFile, "file");

        assertThat(lookup.find(locations(true)).result().output()).isEqualTo("explicit\n");
    }

    @Test
    void shouldPreferTheCustomScriptOverTheDefaultScript() throws IOException {
        script(customScript, "custom");
        script(defaultScript, "default");
        Files.writeString(pinFile, "file");

        assertThat(lookup.find(locations(false)).result().output()).isEqualTo("custom\n");
    }

    @Test
    void shouldPreferTheDefaultScriptOverThePinFile() throws IOException {
        script(defaultScript, "default");
        Files.writeString(pinFile, "file");

        assertThat(lookup.find(locations(false)).result().output()).isEqualTo("default\n");
    }

    @Test
    void shouldReadThePinFileWhenNoScriptExists() throws IOException {
        Files.writeString(pinFile, "0:file\n");

        PinSource source = lookup.find(locations(false));

        assertThat(source.description()).isEqualTo("PIN file " + pinFile);
        assertThat(source.result().success()).isTrue();
        assertThat(source.result().output()).isEqualTo("0:file\n");
    }

    @Test
    void shouldFailWithoutFallbackWhenTheExplicitScriptDoesNotExist() throws IOException {
        script(customScript, "custom");
        Files.writeString(pinFile, "file");

        assertThatThrownBy(() -> lookup.find(locations(true)))
                .isInstanceOf(PinSourceNotFoundException.class)
                .hasMessageContaining(explicitScript.toString());
    }

    @Test
    void shouldNameEveryLocationWhenNoSourceExists() {
        assertThatThrownBy(() -> lookup.find(locations(false)))
                .isInstanceOf(PinSourceNotFoundException.class)
                .hasMessageContainingAll("script path argument (not given)", customScript.toString(),
                        defaultScript.toString(), pinFile.toString());
    }

    @Test
    void shouldReturnAFailedResultWithoutThrowingWhenThePinFileCannotBeRead() throws IOException {
        Files.createDirectory(pinFile);

        assertThatCode(() -> lookup.find(locations(false))).doesNotThrowAnyException();
        PinSource source = lookup.find(locations(false));

        assertThat(source.result().success()).isFalse();
        assertThat(source.result().exitCode()).isNotZero();
    }

    @Test
    void shouldReturnASuccessfulEmptyResultForAnEmptyPinFile() throws IOException {
        Files.writeString(pinFile, "");

        PinSource source = lookup.find(locations(false));

        assertThat(source.result().success()).isTrue();
        assertThat(source.result().output()).isEmpty();
    }

    @Test
    void shouldParseASinglePinFileAsTokenZeroAndTokenLinesPerToken() throws IOException {
        Files.writeString(pinFile, "secret\n");
        List<TokenPin> single = TokenPinMigrator.parsePinOutput(lookup.find(locations(false)).result().output());
        Files.writeString(pinFile, "0:first\n1:second\n");
        List<TokenPin> lines = TokenPinMigrator.parsePinOutput(lookup.find(locations(false)).result().output());

        assertThat(single).containsExactly(new TokenPin("0", "secret"));
        assertThat(lines).containsExactly(new TokenPin("0", "first"), new TokenPin("1", "second"));
    }

    private PinSourceLocations locations(boolean withExplicitScript) {
        return new PinSourceLocations(withExplicitScript ? explicitScript : null, customScript, defaultScript, pinFile);
    }

    private void script(Path path, String output) throws IOException {
        Files.writeString(path, "#!/bin/sh\necho " + output + "\n");
        assertThat(path.toFile().setExecutable(true)).isTrue();
    }
}
