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
package org.niis.xroad.configuration.migration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.migration.tokenpin.TokenPinMigrationResult;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fail-loud contract test for vault-using subcommands in {@link LegacyConfigMigrationCLI}.
 *
 * <p>Verifies that when {@code XROAD_SECRET_STORE_*} env vars are absent, invoking
 * {@code main()} with a vault-using subcommand propagates {@link MigrationException}
 * (not a silent no-op).
 */
@ExtendWith(SystemStubsExtension.class)
class LegacyConfigMigrationCLIVaultTest {

    @SystemStub
    private final EnvironmentVariables envVars = new EnvironmentVariables();

    @TempDir
    Path tempDir;

    @BeforeEach
    void clearVaultEnvVars() {
        // Defense in depth: a developer with a real XROAD_SECRET_STORE_TOKEN exported in their
        // shell would otherwise silently bypass requireEnvVar() and the test would fail
        // confusingly at preflight. Explicitly clear all four env vars before each test.
        envVars.remove("XROAD_SECRET_STORE_HOST");
        envVars.remove("XROAD_SECRET_STORE_PORT");
        envVars.remove("XROAD_SECRET_STORE_SCHEME");
        envVars.remove("XROAD_SECRET_STORE_TOKEN");
    }

    // ----- vault-using subcommands fail loudly when env vars are missing -----

    @Test
    void testSignerTokenPinsWithMissingEnvVarsPropagatesMigrationException() throws IOException {
        Path stubScript = tempDir.resolve("stub-autologin.sh");
        Files.writeString(stubScript, "#!/bin/bash\n");

        assertThatThrownBy(() ->
                LegacyConfigMigrationCLI.main(new String[]{"signer-token-pins", stubScript.toString()}))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("Migration failed")
                .hasRootCauseInstanceOf(XrdRuntimeException.class);
    }

    @Test
    void testPgpKeysWithMissingEnvVarsPropagatesMigrationException() throws IOException {
        Path stubConfig = tempDir.resolve("stub-local.ini");
        Files.writeString(stubConfig, "[section]\nkey=value\n");

        assertThatThrownBy(() ->
                LegacyConfigMigrationCLI.main(new String[]{"pgp-keys", stubConfig.toString()}))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("Migration failed")
                .hasRootCauseInstanceOf(XrdRuntimeException.class);
    }

    @Test
    void testMessageLogKeysWithMissingEnvVarsPropagatesMigrationException() throws IOException {
        Path stubKeystore = tempDir.resolve("stub-keystore.p12");
        Files.writeString(stubKeystore, "stub bytes");

        assertThatThrownBy(() ->
                LegacyConfigMigrationCLI.main(new String[]{
                        "messagelog-db-encryption-keys",
                        stubKeystore.toString(),
                        "test-password",
                        "test-key-id"
                }))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("Migration failed")
                .hasRootCauseInstanceOf(XrdRuntimeException.class);
    }

    // ----- migrateSignerTokenPins fail-loud on PARTIAL_SUCCESS / FAILED -----

    @Test
    void testTokenPinMigrationResultIsSuccessfulContract() {
        var failed = TokenPinMigrationResult.failed("simulated complete failure");
        var partial = TokenPinMigrationResult.partialSuccess(
                java.util.List.of("tok-ok"),
                java.util.List.of(),
                java.util.Map.of("tok-bad", "permission denied"));
        var success = TokenPinMigrationResult.success(java.util.List.of("tok-ok"));
        var skipped = TokenPinMigrationResult.skipped("nothing to migrate");

        assertThat(failed.isSuccessful()).isFalse();
        assertThat(partial.isSuccessful()).isFalse();
        assertThat(success.isSuccessful()).isTrue();
        assertThat(skipped.isSuccessful()).isTrue();
    }
}
