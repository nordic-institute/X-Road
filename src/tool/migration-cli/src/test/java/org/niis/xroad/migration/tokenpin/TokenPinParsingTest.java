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
    void tokenPin_validInputs_createsRecord() {
        // Given/When
        TokenPin pin = new TokenPin("0", "secret123");

        // Then
        assertEquals("0", pin.tokenId());
        assertEquals("secret123", pin.pin());
    }

    @Test
    void tokenPin_nullTokenId_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new TokenPin(null, "secret"));
    }

    @Test
    void tokenPin_blankTokenId_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new TokenPin("  ", "secret"));
    }

    @Test
    void tokenPin_emptyTokenId_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new TokenPin("", "secret"));
    }

    @Test
    void tokenPin_nullPin_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new TokenPin("0", null));
    }

    @Test
    void tokenPin_blankPin_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new TokenPin("0", "  "));
    }

    @Test
    void tokenPin_emptyPin_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new TokenPin("0", ""));
    }

    @Test
    void tokenPin_tokenIdWithHyphens_allowed() {
        // Given/When
        TokenPin pin = new TokenPin("softtoken-1", "secret");

        // Then
        assertEquals("softtoken-1", pin.tokenId());
    }

    @Test
    void tokenPin_tokenIdWithUnderscores_allowed() {
        // Given/When
        TokenPin pin = new TokenPin("soft_token_1", "secret");

        // Then
        assertEquals("soft_token_1", pin.tokenId());
    }

    @Test
    void tokenPin_pinWithColons_allowed() {
        // Given/When: PIN containing colons should be preserved
        TokenPin pin = new TokenPin("0", "secret:with:colons");

        // Then
        assertEquals("secret:with:colons", pin.pin());
    }

    @Test
    void tokenPin_pinWithSpecialChars_allowed() {
        // Given/When
        TokenPin pin = new TokenPin("0", "s3cr3t!@#$%");

        // Then
        assertEquals("s3cr3t!@#$%", pin.pin());
    }

    @Test
    void scriptExecutionResult_exitCode0_successAndHasPins() {
        // Given
        ScriptExecutionResult result = new ScriptExecutionResult(true, "secret", "", 0);

        // Then
        assertEquals(true, result.success());
        assertEquals(true, result.hasPins());
        assertEquals(false, result.isPinUnavailable());
    }

    @Test
    void scriptExecutionResult_exitCode127_successButNoPins() {
        // Given
        ScriptExecutionResult result = new ScriptExecutionResult(true, "", "no pin", 127);

        // Then
        assertEquals(true, result.success());
        assertEquals(false, result.hasPins());
        assertEquals(true, result.isPinUnavailable());
    }

    @Test
    void scriptExecutionResult_exitCode1_failureNoPins() {
        // Given
        ScriptExecutionResult result = new ScriptExecutionResult(false, "", "error", 1);

        // Then
        assertEquals(false, result.success());
        assertEquals(false, result.hasPins());
        assertEquals(false, result.isPinUnavailable());
    }

    @Test
    void scriptExecutionResult_emptyOutput_noPins() {
        // Given
        ScriptExecutionResult result = new ScriptExecutionResult(true, "", "", 0);

        // Then
        assertEquals(true, result.success());
        assertEquals(false, result.hasPins());
    }

    @Test
    void scriptExecutionResult_blankOutput_noPins() {
        // Given
        ScriptExecutionResult result = new ScriptExecutionResult(true, "   ", "", 0);

        // Then
        assertEquals(true, result.success());
        assertEquals(false, result.hasPins());
    }

    @Test
    void migrationResult_success_factory() {
        // Given/When
        TokenPinMigrationResult result = TokenPinMigrationResult.success(java.util.List.of("0", "1"));

        // Then
        assertEquals(MigrationStatus.SUCCESS, result.status());
        assertEquals(2, result.successfulTokens().size());
        assertEquals(true, result.isSuccessful());
    }

    @Test
    void migrationResult_failed_factory() {
        // Given/When
        TokenPinMigrationResult result = TokenPinMigrationResult.failed("Test error");

        // Then
        assertEquals(MigrationStatus.FAILED, result.status());
        assertEquals(true, result.message().contains("Test error"));
        assertEquals(false, result.isSuccessful());
    }

    @Test
    void migrationResult_skipped_isSuccessful() {
        // Given/When
        TokenPinMigrationResult result = TokenPinMigrationResult.skipped("No PINs");

        // Then
        assertEquals(MigrationStatus.SKIPPED, result.status());
        assertEquals(true, result.isSuccessful());
    }

    @Test
    void migrationResult_partialSuccess_factory() {
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
