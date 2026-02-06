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
import org.niis.xroad.common.vault.VaultClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Migrates token PINs from legacy xroad-autologin scripts to OpenBao.
 * Executed once during package upgrade for existing installations with xroad-autologin package.
 */
@Slf4j
@RequiredArgsConstructor
public class TokenPinMigrator {

    private final VaultClient vaultClient;
    private final AutoLoginScriptExecutor scriptExecutor;

    /**
     * Migrates PINs from autologin scripts to OpenBao.
     *
     * @param scriptPath Path to fetch-pin script (custom-fetch-pin.sh or default-fetch-pin.sh)
     * @return Migration result with status and details
     * @throws IOException If script execution fails critically
     */
    public TokenPinMigrationResult migrateFromScript(Path scriptPath) throws IOException {
        log.info("Starting PIN migration from autologin script: {}", scriptPath);

        validateScript(scriptPath);

        ScriptExecutionResult scriptResult = scriptExecutor.execute(scriptPath);

        TokenPinMigrationResult earlyResult = handleScriptResult(scriptResult);
        if (earlyResult != null) {
            return earlyResult;
        }

        List<TokenPin> tokenPins;
        try {
            tokenPins = parsePinOutput(scriptResult.output());
        } catch (IllegalArgumentException e) {
            log.error("Failed to parse script output: {}", e.getMessage());
            return TokenPinMigrationResult.failed("Parse error: " + e.getMessage());
        }

        if (tokenPins.isEmpty()) {
            log.info("No PINs found in script output, skipping migration");
            return TokenPinMigrationResult.skipped("No PINs in script output");
        }

        log.info("Parsed {} token PIN(s) from script output", tokenPins.size());

        return migrateTokenPins(tokenPins);
    }

    private void validateScript(Path scriptPath) throws IOException {
        if (!Files.exists(scriptPath)) {
            throw new IOException("Script not found: " + scriptPath);
        }
        if (!Files.isExecutable(scriptPath)) {
            throw new IOException("Script not executable: " + scriptPath);
        }
    }

    private TokenPinMigrationResult handleScriptResult(ScriptExecutionResult scriptResult) {
        if (scriptResult.isPinUnavailable()) {
            log.info("No PINs available (exit code 127), skipping migration");
            return TokenPinMigrationResult.skipped("No PINs available from script");
        }
        if (!scriptResult.success()) {
            log.error("Script execution failed with exit code {}: {}",
                    scriptResult.exitCode(), scriptResult.errorOutput());
            return TokenPinMigrationResult.failed(
                    "Script failed with exit code " + scriptResult.exitCode() + ": " + scriptResult.errorOutput());
        }
        return null;
    }

    private TokenPinMigrationResult migrateTokenPins(List<TokenPin> tokenPins) {
        List<String> successfulTokens = new ArrayList<>();
        List<String> skippedTokens = new ArrayList<>();
        Map<String, String> failedTokens = new HashMap<>();

        for (TokenPin tokenPin : tokenPins) {
            migrateTokenPin(tokenPin, successfulTokens, skippedTokens, failedTokens);
        }

        return buildMigrationResult(successfulTokens, skippedTokens, failedTokens);
    }

    private void migrateTokenPin(TokenPin tokenPin, List<String> successfulTokens,
                                  List<String> skippedTokens, Map<String, String> failedTokens) {
        try {
            Optional<char[]> existing = vaultClient.getTokenPin(tokenPin.tokenId());
            if (existing.isPresent()) {
                log.info("PIN for token {} already exists in OpenBao, skipping", tokenPin.tokenId());
                skippedTokens.add(tokenPin.tokenId());
                return;
            }

            vaultClient.setTokenPin(tokenPin.tokenId(), tokenPin.pin().toCharArray());
            log.info("Stored PIN for token {} in OpenBao", tokenPin.tokenId());

            if (!verifyPinInVault(tokenPin.tokenId())) {
                log.error("PIN verification failed for token {}", tokenPin.tokenId());
                failedTokens.put(tokenPin.tokenId(), "Verification failed after storage");
                return;
            }

            successfulTokens.add(tokenPin.tokenId());
            log.info("Migrated PIN for token {} to OpenBao", tokenPin.tokenId());

        } catch (Exception e) {
            log.error("Failed to migrate PIN for token {}: {}", tokenPin.tokenId(), e.getMessage());
            failedTokens.put(tokenPin.tokenId(), e.getMessage());
        }
    }

    private TokenPinMigrationResult buildMigrationResult(List<String> successfulTokens,
                                                          List<String> skippedTokens,
                                                          Map<String, String> failedTokens) {
        if (failedTokens.isEmpty() && skippedTokens.isEmpty()) {
            return TokenPinMigrationResult.success(successfulTokens);
        }
        if (successfulTokens.isEmpty() && skippedTokens.isEmpty()) {
            return TokenPinMigrationResult.failed("All tokens failed to migrate");
        }
        return TokenPinMigrationResult.partialSuccess(successfulTokens, skippedTokens, failedTokens);
    }

    /**
     * Verifies a single PIN is accessible in OpenBao.
     *
     * @param tokenId Token ID to verify
     * @return true if PIN is retrievable and non-empty
     */
    public boolean verifyPinInVault(String tokenId) {
        try {
            Optional<char[]> pin = vaultClient.getTokenPin(tokenId);
            if (pin.isEmpty()) {
                log.warn("PIN verification failed for token {}: not found", tokenId);
                return false;
            }
            if (pin.get().length == 0) {
                log.warn("PIN verification failed for token {}: empty value", tokenId);
                return false;
            }
            log.debug("Verified PIN for token {} in OpenBao", tokenId);
            return true;
        } catch (Exception e) {
            log.error("PIN verification failed for token {}: {}", tokenId, e.getMessage());
            return false;
        }
    }

    /**
     * Parse fetch-pin script output into TokenPin objects.
     * Supports both single-PIN format (default token "0") and multi-token format (tokenId:pin per line).
     */
    private List<TokenPin> parsePinOutput(String output) {
        List<TokenPin> result = new ArrayList<>();
        Set<String> seenTokenIds = new HashSet<>();
        String[] lines = output.split("\\R"); // Handles \n, \r\n, \r

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            String tokenId;
            String pin;

            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                // Multi-token format: tokenId:pin
                tokenId = line.substring(0, colonIndex).trim();
                pin = line.substring(colonIndex + 1).trim();
            } else {
                // Single PIN format (default token "0")
                tokenId = "0";
                pin = line;
            }

            // Validate (avoid logging sensitive line content)
            if (tokenId.isEmpty()) {
                throw new IllegalArgumentException("Empty token ID found in script output");
            }
            if (pin.isEmpty()) {
                throw new IllegalArgumentException("Empty PIN for token: " + tokenId);
            }

            // Check for duplicates
            if (!seenTokenIds.add(tokenId)) {
                throw new IllegalArgumentException("Duplicate token ID in script output: " + tokenId);
            }

            result.add(new TokenPin(tokenId, pin));
        }

        return result;
    }
}
