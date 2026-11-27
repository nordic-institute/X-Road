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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Decides where the token PINs come from and reads them. The order is: the script path given by the operator,
 * the custom fetch-pin script, the default fetch-pin script, the legacy PIN file.
 * Scripts are run, the PIN file is read directly; both give text in the format that
 * {@link TokenPinMigrator#parsePinOutput(String)} accepts.
 */
@Slf4j
@RequiredArgsConstructor
public class PinSourceLookup {

    private static final int FAILURE_EXIT_CODE = 1;

    private final AutoLoginScriptExecutor scriptExecutor;

    /**
     * Picks the first source that exists and reads it. An explicit script that does not exist is an error;
     * the other sources are then not tried.
     *
     * @throws PinSourceNotFoundException if the explicit script does not exist, or no source exists; the message
     *                                    names every location checked
     * @throws IOException                if a script cannot be run
     */
    public PinSource find(PinSourceLocations locations) throws IOException {
        if (locations.explicitScript() != null) {
            if (!Files.exists(locations.explicitScript())) {
                throw new PinSourceNotFoundException("Fetch-pin script not found: " + locations.explicitScript());
            }
            return runScript("script " + locations.explicitScript(), locations.explicitScript());
        }
        if (Files.exists(locations.customScript())) {
            return runScript("custom fetch-pin script " + locations.customScript(), locations.customScript());
        }
        if (Files.exists(locations.defaultScript())) {
            return runScript("default fetch-pin script " + locations.defaultScript(), locations.defaultScript());
        }
        if (Files.exists(locations.pinFile())) {
            return readPinFile(locations.pinFile());
        }
        throw new PinSourceNotFoundException("No PIN source found. Checked, in order: " + checkedLocations(locations));
    }

    private static String checkedLocations(PinSourceLocations locations) {
        String explicit = locations.explicitScript() == null
                ? "script path argument (not given)" : locations.explicitScript().toString();
        return Stream.of(explicit, locations.customScript().toString(), locations.defaultScript().toString(),
                locations.pinFile().toString()).collect(Collectors.joining(", "));
    }

    private PinSource runScript(String description, Path script) throws IOException {
        return new PinSource(description, scriptExecutor.execute(script));
    }

    private PinSource readPinFile(Path pinFile) {
        String description = "PIN file " + pinFile;
        try {
            return new PinSource(description,
                    new ScriptExecutionResult(true, Files.readString(pinFile, StandardCharsets.UTF_8), "", 0));
        } catch (IOException e) {
            log.warn("Cannot read {}: {}", description, e.getClass().getSimpleName());
            return new PinSource(description, new ScriptExecutionResult(false, "",
                    "cannot read " + pinFile + " (" + e.getClass().getSimpleName() + ")", FAILURE_EXIT_CODE));
        }
    }
}
