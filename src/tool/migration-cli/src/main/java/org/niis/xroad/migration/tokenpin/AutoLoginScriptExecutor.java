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

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Executes fetch-pin shell scripts and captures their output.
 * Handles timeout to prevent hanging on unresponsive scripts.
 */
@Slf4j
public class AutoLoginScriptExecutor {

    private static final long DEFAULT_TIMEOUT_SECONDS = 30;

    private final long timeoutSeconds;

    /**
     * Creates an executor with the default timeout (30 seconds).
     */
    public AutoLoginScriptExecutor() {
        this(DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * Creates an executor with a custom timeout.
     *
     * @param timeoutSeconds Maximum time to wait for script execution
     */
    public AutoLoginScriptExecutor(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Executes the fetch-pin script and returns the result.
     *
     * @param scriptPath Path to the script to execute
     * @return ScriptExecutionResult with output, error output, and exit code
     * @throws IOException If script not found, not executable, or execution fails critically
     */
    public ScriptExecutionResult execute(Path scriptPath) throws IOException {
        log.debug("Executing fetch-pin script: {}", scriptPath);

        // Validate script exists
        if (!Files.exists(scriptPath)) {
            throw new IOException("Script not found: " + scriptPath);
        }

        // Validate script is executable
        if (!Files.isExecutable(scriptPath)) {
            throw new IOException("Script not executable: " + scriptPath);
        }

        ProcessBuilder pb = new ProcessBuilder(scriptPath.toString());
        pb.redirectErrorStream(false); // Keep stdout and stderr separate

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new IOException("Failed to start script: " + e.getMessage(), e);
        }

        // Wait for completion with timeout FIRST
        // (reading streams before waitFor blocks until process exits)
        boolean completed;
        try {
            completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Script execution interrupted");
        }

        if (!completed) {
            process.destroyForcibly();
            throw new IOException("Script execution timed out after " + timeoutSeconds + " seconds");
        }

        // Read stdout and stderr (process has completed, streams are ready)
        String output;
        String errorOutput;
        try {
            output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            errorOutput = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IOException("Failed to read script output: " + e.getMessage(), e);
        }

        int exitCode = process.exitValue();
        log.debug("Script completed with exit code: {}", exitCode);

        // Determine success based on exit code
        // Exit code 127 is special: means "no PIN available" but not an error
        boolean success = (exitCode == 0 || exitCode == 127);

        return new ScriptExecutionResult(success, output, errorOutput, exitCode);
    }
}
