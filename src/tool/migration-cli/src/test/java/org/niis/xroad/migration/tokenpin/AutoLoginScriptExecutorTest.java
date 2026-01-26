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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoLoginScriptExecutorTest {

    @TempDir
    Path tempDir;

    private AutoLoginScriptExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new AutoLoginScriptExecutor(5); // 5 second timeout for tests
    }

    @Test
    void executeSuccessfulScriptReturnsOutput() throws IOException {
        // Given: Script that outputs a PIN
        Path script = createScript("#!/bin/bash\necho 'secret123'");

        // When
        ScriptExecutionResult result = executor.execute(script);

        // Then
        assertTrue(result.success());
        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("secret123"));
        assertTrue(result.hasPins());
    }

    @Test
    void executeExitCode127MarkedAsSuccess() throws IOException {
        // Given: Script that exits with 127
        Path script = createScript("#!/bin/bash\nexit 127");

        // When
        ScriptExecutionResult result = executor.execute(script);

        // Then
        assertTrue(result.success());
        assertTrue(result.isPinUnavailable());
        assertEquals(127, result.exitCode());
        assertFalse(result.hasPins());
    }

    @Test
    void executeScriptFailsReturnsFailure() throws IOException {
        // Given: Script that fails
        Path script = createScript("#!/bin/bash\necho 'error' >&2\nexit 1");

        // When
        ScriptExecutionResult result = executor.execute(script);

        // Then
        assertFalse(result.success());
        assertEquals(1, result.exitCode());
        assertTrue(result.errorOutput().contains("error"));
    }

    @Test
    void executeScriptNotFoundThrowsIOException() {
        // Given: Non-existent script
        Path script = tempDir.resolve("nonexistent.sh");

        // When/Then
        IOException exception = assertThrows(IOException.class,
                () -> executor.execute(script));
        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    void executeScriptNotExecutableThrowsIOException() throws IOException {
        // Given: Non-executable script
        Path script = tempDir.resolve("notexec.sh");
        Files.writeString(script, "#!/bin/bash\necho 'test'");
        // Note: Don't set executable permission

        // When/Then
        IOException exception = assertThrows(IOException.class,
                () -> executor.execute(script));
        assertTrue(exception.getMessage().contains("not executable"));
    }

    @Test
    void executeScriptTimeoutThrowsIOException() throws IOException {
        // Given: Script that hangs (use a very short sleep to be reliable)
        Path script = createScript("#!/bin/bash\nsleep 10");
        // Use a very short timeout to ensure the test completes quickly
        AutoLoginScriptExecutor shortTimeoutExecutor = new AutoLoginScriptExecutor(1);

        // When/Then
        IOException exception = assertThrows(IOException.class,
                () -> shortTimeoutExecutor.execute(script));
        assertTrue(exception.getMessage().contains("timed out"), "Expected timeout message but got: " + exception.getMessage());
    }

    @Test
    void executeMultiTokenOutputCapturesAll() throws IOException {
        // Given: Script that outputs multiple tokens
        Path script = createScript("""
                #!/bin/bash
                echo '0:secret1'
                echo 'softtoken-1:secret2'
                """);

        // When
        ScriptExecutionResult result = executor.execute(script);

        // Then
        assertTrue(result.success());
        assertTrue(result.output().contains("0:secret1"));
        assertTrue(result.output().contains("softtoken-1:secret2"));
    }

    @Test
    void executeStderrCapturedSeparately() throws IOException {
        // Given: Script with both stdout and stderr
        Path script = createScript("""
                #!/bin/bash
                echo 'PIN output'
                echo 'Warning message' >&2
                """);

        // When
        ScriptExecutionResult result = executor.execute(script);

        // Then
        assertTrue(result.output().contains("PIN output"));
        assertTrue(result.errorOutput().contains("Warning message"));
    }

    @Test
    void executeEmptyOutputHasPinsReturnsFalse() throws IOException {
        // Given: Script that outputs nothing
        Path script = createScript("#!/bin/bash\nexit 0");

        // When
        ScriptExecutionResult result = executor.execute(script);

        // Then
        assertTrue(result.success());
        assertEquals(0, result.exitCode());
        assertFalse(result.hasPins());
    }

    @Test
    void executeNonZeroNonSpecialExitCodeMarkedAsFailure() throws IOException {
        // Given: Script that exits with non-zero, non-127 code
        Path script = createScript("#!/bin/bash\nexit 2");

        // When
        ScriptExecutionResult result = executor.execute(script);

        // Then
        assertFalse(result.success());
        assertEquals(2, result.exitCode());
    }

    private Path createScript(String content) throws IOException {
        Path script = tempDir.resolve("test-script-" + System.nanoTime() + ".sh");
        Files.writeString(script, content);
        script.toFile().setExecutable(true);
        return script;
    }
}
