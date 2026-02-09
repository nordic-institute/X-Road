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

import java.util.List;
import java.util.Map;

/**
 * Result of a token PIN migration operation.
 *
 * @param status Overall migration status
 * @param successfulTokens Token IDs successfully migrated
 * @param skippedTokens Token IDs skipped (already exist in OpenBao)
 * @param failedTokens Token ID to error message for failures
 * @param message Human-readable result summary
 */
public record TokenPinMigrationResult(
        MigrationStatus status,
        List<String> successfulTokens,
        List<String> skippedTokens,
        Map<String, String> failedTokens,
        String message
) {

    /**
     * Creates a success result for successfully migrated tokens.
     */
    public static TokenPinMigrationResult success(List<String> tokens) {
        return new TokenPinMigrationResult(
                MigrationStatus.SUCCESS,
                List.copyOf(tokens),
                List.of(),
                Map.of(),
                "Successfully migrated " + tokens.size() + " token PIN(s)"
        );
    }

    /**
     * Creates a partial success result when some tokens succeeded and some failed/skipped.
     */
    public static TokenPinMigrationResult partialSuccess(
            List<String> successful,
            List<String> skipped,
            Map<String, String> failed) {
        return new TokenPinMigrationResult(
                MigrationStatus.PARTIAL_SUCCESS,
                List.copyOf(successful),
                List.copyOf(skipped),
                Map.copyOf(failed),
                String.format("Migrated %d, skipped %d, failed %d",
                        successful.size(), skipped.size(), failed.size())
        );
    }

    /**
     * Creates a failed result when migration fails completely.
     */
    public static TokenPinMigrationResult failed(String reason) {
        return new TokenPinMigrationResult(
                MigrationStatus.FAILED,
                List.of(),
                List.of(),
                Map.of(),
                "Migration failed: " + reason
        );
    }

    /**
     * Creates a skipped result when there are no PINs to migrate.
     */
    public static TokenPinMigrationResult skipped(String reason) {
        return new TokenPinMigrationResult(
                MigrationStatus.SKIPPED,
                List.of(),
                List.of(),
                Map.of(),
                reason
        );
    }

    /**
     * Returns true if migration was successful or skipped (non-failure states).
     */
    public boolean isSuccessful() {
        return status == MigrationStatus.SUCCESS || status == MigrationStatus.SKIPPED;
    }
}
