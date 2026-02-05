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
import org.niis.xroad.common.vault.VaultClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenPinMigratorTest {

    @TempDir
    Path tempDir;

    private VaultClient mockVaultClient;
    private AutoLoginScriptExecutor mockExecutor;
    private TokenPinMigrator migrator;

    @BeforeEach
    void setUp() {
        mockVaultClient = mock(VaultClient.class);
        mockExecutor = mock(AutoLoginScriptExecutor.class);
        migrator = new TokenPinMigrator(mockVaultClient, mockExecutor);
    }

    @Test
    void migrateFromScriptSinglePinSuccess() throws IOException {
        // Given: script returns single PIN
        Path script = createExecutableScript();
        when(mockExecutor.execute(any()))
                .thenReturn(new ScriptExecutionResult(true, "secret123\n", "", 0));
        when(mockVaultClient.getTokenPin("0"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of("secret123".toCharArray()));

        // When
        TokenPinMigrationResult result = migrator.migrateFromScript(script);

        // Then
        assertEquals(MigrationStatus.SUCCESS, result.status());
        assertEquals(List.of("0"), result.successfulTokens());
        verify(mockVaultClient).setTokenPin(eq("0"), any(char[].class));
    }

    @Test
    void migrateFromScriptMultiTokenSuccess() throws IOException {
        // Given: script returns multiple tokens
        Path script = createExecutableScript();
        String output = "0:secret123\nsofttoken-1:another\n";
        when(mockExecutor.execute(any()))
                .thenReturn(new ScriptExecutionResult(true, output, "", 0));
        // First token "0": existence check returns empty, verification returns value
        when(mockVaultClient.getTokenPin("0"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of("secret123".toCharArray()));
        // Second token "softtoken-1": existence check returns empty, verification returns value
        when(mockVaultClient.getTokenPin("softtoken-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of("another".toCharArray()));

        // When
        TokenPinMigrationResult result = migrator.migrateFromScript(script);

        // Then
        assertEquals(MigrationStatus.SUCCESS, result.status());
        assertEquals(2, result.successfulTokens().size());
        assertTrue(result.successfulTokens().contains("0"));
        assertTrue(result.successfulTokens().contains("softtoken-1"));
    }

    @Test
    void migrateFromScriptExitCode127Skipped() throws IOException {
        // Given: script returns exit code 127
        Path script = createExecutableScript();
        when(mockExecutor.execute(any()))
                .thenReturn(new ScriptExecutionResult(true, "", "PIN not available", 127));

        // When
        TokenPinMigrationResult result = migrator.migrateFromScript(script);

        // Then
        assertEquals(MigrationStatus.SKIPPED, result.status());
        assertTrue(result.message().contains("No PINs available"));
        verify(mockVaultClient, never()).setTokenPin(anyString(), any());
    }

    @Test
    void migrateFromScriptExistingPinSkipped() throws IOException {
        // Given: PIN already exists in OpenBao
        Path script = createExecutableScript();
        when(mockExecutor.execute(any()))
                .thenReturn(new ScriptExecutionResult(true, "secret123\n", "", 0));
        when(mockVaultClient.getTokenPin("0"))
                .thenReturn(Optional.of("existing".toCharArray()));

        // When
        TokenPinMigrationResult result = migrator.migrateFromScript(script);

        // Then
        assertTrue(result.skippedTokens().contains("0"));
        verify(mockVaultClient, never()).setTokenPin(anyString(), any());
    }

    @Test
    void migrateFromScriptDuplicateTokenIdFailed() throws IOException {
        // Given: script output has duplicate token ID
        Path script = createExecutableScript();
        String output = "0:secret1\n0:secret2\n";
        when(mockExecutor.execute(any()))
                .thenReturn(new ScriptExecutionResult(true, output, "", 0));

        // When
        TokenPinMigrationResult result = migrator.migrateFromScript(script);

        // Then
        assertEquals(MigrationStatus.FAILED, result.status());
        assertTrue(result.message().contains("Duplicate token ID"));
    }

    @Test
    void migrateFromScriptEmptyOutputSkipped() throws IOException {
        // Given: script returns empty output
        Path script = createExecutableScript();
        when(mockExecutor.execute(any()))
                .thenReturn(new ScriptExecutionResult(true, "", "", 0));

        // When
        TokenPinMigrationResult result = migrator.migrateFromScript(script);

        // Then
        assertEquals(MigrationStatus.SKIPPED, result.status());
        assertTrue(result.message().contains("No PINs"));
    }

    @Test
    void migrateFromScriptScriptFailureFailed() throws IOException {
        // Given: script execution fails
        Path script = createExecutableScript();
        when(mockExecutor.execute(any()))
                .thenReturn(new ScriptExecutionResult(false, "", "Script error", 1));

        // When
        TokenPinMigrationResult result = migrator.migrateFromScript(script);

        // Then
        assertEquals(MigrationStatus.FAILED, result.status());
        assertTrue(result.message().contains("Script failed"));
    }

    @Test
    void migrateFromScriptVerificationFailsPartialSuccess() throws IOException {
        // Given: script returns two tokens but one fails verification
        Path script = createExecutableScript();
        String output = "0:secret1\n1:secret2\n";
        when(mockExecutor.execute(any()))
                .thenReturn(new ScriptExecutionResult(true, output, "", 0));
        // First token: exists check returns empty, then verification succeeds
        when(mockVaultClient.getTokenPin("0"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of("secret1".toCharArray()));
        // Second token: exists check returns empty, then verification fails
        when(mockVaultClient.getTokenPin("1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());

        // When
        TokenPinMigrationResult result = migrator.migrateFromScript(script);

        // Then
        assertEquals(MigrationStatus.PARTIAL_SUCCESS, result.status());
        assertTrue(result.successfulTokens().contains("0"));
        assertTrue(result.failedTokens().containsKey("1"));
    }

    @Test
    void verifyPinInVaultExistingPinReturnsTrue() {
        // Given: PIN exists in vault
        when(mockVaultClient.getTokenPin("0"))
                .thenReturn(Optional.of("secret".toCharArray()));

        // When
        boolean result = migrator.verifyPinInVault("0");

        // Then
        assertTrue(result);
    }

    @Test
    void verifyPinInVaultMissingPinReturnsFalse() {
        // Given: PIN does not exist in vault
        when(mockVaultClient.getTokenPin("0"))
                .thenReturn(Optional.empty());

        // When
        boolean result = migrator.verifyPinInVault("0");

        // Then
        assertEquals(false, result);
    }

    @Test
    void verifyPinInVaultEmptyPinReturnsFalse() {
        // Given: PIN is empty
        when(mockVaultClient.getTokenPin("0"))
                .thenReturn(Optional.of(new char[0]));

        // When
        boolean result = migrator.verifyPinInVault("0");

        // Then
        assertEquals(false, result);
    }

    @Test
    void verifyPinInVaultVaultThrowsReturnsFalse() {
        // Given: VaultClient throws exception
        when(mockVaultClient.getTokenPin("0"))
                .thenThrow(new RuntimeException("Vault error"));

        // When
        boolean result = migrator.verifyPinInVault("0");

        // Then
        assertEquals(false, result);
    }

    private Path createExecutableScript() throws IOException {
        Path script = tempDir.resolve("test-script-" + System.nanoTime() + ".sh");
        Files.writeString(script, "#!/bin/bash\necho 'test'");
        script.toFile().setExecutable(true);
        return script;
    }
}
