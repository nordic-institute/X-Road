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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TokenPinParsingTest {

    @Test
    void tokenPinValidInputsCreatesRecord() {
        // Given/When
        TokenPin pin = new TokenPin("0", "secret123");

        // Then
        assertEquals("0", pin.tokenId());
        assertEquals("secret123", pin.pin());
    }

    @Test
    void tokenPinNullTokenIdThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new TokenPin(null, "secret"));
    }

    @Test
    void tokenPinBlankTokenIdThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new TokenPin("  ", "secret"));
    }

    @Test
    void tokenPinEmptyTokenIdThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new TokenPin("", "secret"));
    }

    @Test
    void tokenPinNullPinThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new TokenPin("0", null));
    }

    @Test
    void tokenPinBlankPinThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new TokenPin("0", "  "));
    }

    @Test
    void tokenPinEmptyPinThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new TokenPin("0", ""));
    }

    @Test
    void tokenPinTokenIdWithHyphensAllowed() {
        // Given/When
        TokenPin pin = new TokenPin("softtoken-1", "secret");

        // Then
        assertEquals("softtoken-1", pin.tokenId());
    }

    @Test
    void tokenPinTokenIdWithUnderscoresAllowed() {
        // Given/When
        TokenPin pin = new TokenPin("soft_token_1", "secret");

        // Then
        assertEquals("soft_token_1", pin.tokenId());
    }

    @Test
    void tokenPinPinWithColonsAllowed() {
        // Given/When: PIN containing colons should be preserved
        TokenPin pin = new TokenPin("0", "secret:with:colons");

        // Then
        assertEquals("secret:with:colons", pin.pin());
    }

    @Test
    void tokenPinPinWithSpecialCharsAllowed() {
        // Given/When
        TokenPin pin = new TokenPin("0", "s3cr3t!@#$%");

        // Then
        assertEquals("s3cr3t!@#$%", pin.pin());
    }

    @Test
    void scriptExecutionResultExitCode0SuccessAndHasPins() {
        // Given
        ScriptExecutionResult result = new ScriptExecutionResult(true, "secret", "", 0);

        // Then
        assertEquals(true, result.success());
        assertEquals(true, result.hasPins());
        assertEquals(false, result.isPinUnavailable());
    }

    @Test
    void scriptExecutionResultExitCode127SuccessButNoPins() {
        // Given
        ScriptExecutionResult result = new ScriptExecutionResult(true, "", "no pin", 127);

        // Then
        assertEquals(true, result.success());
        assertEquals(false, result.hasPins());
        assertEquals(true, result.isPinUnavailable());
    }

    @Test
    void scriptExecutionResultExitCode1FailureNoPins() {
        // Given
        ScriptExecutionResult result = new ScriptExecutionResult(false, "", "error", 1);

        // Then
        assertEquals(false, result.success());
        assertEquals(false, result.hasPins());
        assertEquals(false, result.isPinUnavailable());
    }

    @Test
    void scriptExecutionResultEmptyOutputNoPins() {
        // Given
        ScriptExecutionResult result = new ScriptExecutionResult(true, "", "", 0);

        // Then
        assertEquals(true, result.success());
        assertEquals(false, result.hasPins());
    }

    @Test
    void scriptExecutionResultBlankOutputNoPins() {
        // Given
        ScriptExecutionResult result = new ScriptExecutionResult(true, "   ", "", 0);

        // Then
        assertEquals(true, result.success());
        assertEquals(false, result.hasPins());
    }

    @Test
    void migrationResultSuccessFactory() {
        // Given/When
        TokenPinMigrationResult result = TokenPinMigrationResult.success(java.util.List.of("0", "1"));

        // Then
        assertEquals(MigrationStatus.SUCCESS, result.status());
        assertEquals(2, result.successfulTokens().size());
        assertEquals(true, result.isSuccessful());
    }

    @Test
    void migrationResultFailedFactory() {
        // Given/When
        TokenPinMigrationResult result = TokenPinMigrationResult.failed("Test error");

        // Then
        assertEquals(MigrationStatus.FAILED, result.status());
        assertEquals(true, result.message().contains("Test error"));
        assertEquals(false, result.isSuccessful());
    }

    @Test
    void migrationResultSkippedIsSuccessful() {
        // Given/When
        TokenPinMigrationResult result = TokenPinMigrationResult.skipped("No PINs");

        // Then
        assertEquals(MigrationStatus.SKIPPED, result.status());
        assertEquals(true, result.isSuccessful());
    }

    @Test
    void migrationResultPartialSuccessFactory() {
        // Given/When
        TokenPinMigrationResult result = TokenPinMigrationResult.partialSuccess(
                java.util.List.of("0"),
                java.util.List.of("1"),
                java.util.Map.of("2", "error")
        );

        // Then
        assertEquals(MigrationStatus.PARTIAL_SUCCESS, result.status());
        assertEquals(1, result.successfulTokens().size());
        assertEquals(1, result.skippedTokens().size());
        assertEquals(1, result.failedTokens().size());
        assertEquals(false, result.isSuccessful());
    }
}
