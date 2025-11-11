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

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Paths;

/**
 * Unified CLI for X-Road migrations.
 * Supports:
 * - Configuration migration (INI/properties to DB)
 * - PGP key migration (GPG to Vault)
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LegacyConfigMigrationCLI {

    private static final String OPTION_PGP_KEYS = "--pgp-keys";

    public static void main(String[] args) {
        try {
            if (args.length > 0 && OPTION_PGP_KEYS.equals(args[0])) {
                migratePgpKeys(args);
            } else {
                migrateConfiguration(args);
            }
        } catch (Exception e) {
            log.error("Migration failed: {}", e.getMessage(), e);
            throw new MigrationException("Migration failed", e);
        }
    }

    private static void migratePgpKeys(String[] args) {
        if (args.length < 2) {
            logPgpUsageAndThrow("Configuration file required");
        }

        String configFile = args[1];

        validateFilePath(configFile, "configuration");

        if (!new File(configFile).exists()) {
            throw new IllegalArgumentException("Configuration file does not exist: " + configFile);
        }

        log.info("Starting PGP key migration");
        log.info("  INI config: {}", configFile);

        log.warn("PGP key migration requires Vault configuration");
        log.info("Configuration file validated: {}", configFile);
        log.info("To complete migration, ensure Vault is configured and accessible");

        //TODO provide vault configuration

        // Note: PGP key migration requires Vault configuration and is not yet implemented in CLI
        // Example of how this would work with proper vault client:
        // VaultClient vaultClient = createVaultClient();
        // PgpKeyMigrator migrator = new PgpKeyMigrator(vaultClient);
        // MigrationResult result = migrator.migrateFromConfig(Paths.get(configFile));
        // log.info("Migration result: {}", result);
        // log.info("Keys stored in Vault");

        // Configuration migration should be done separately:
        // MessageLogIniToDbMigrator.main(new String[] { mappingFile, dbPropertiesFile });
    }

    private static void migrateConfiguration(String[] args) {
        validateConfigArgs(args);

        try {
            var input = args[0];

            if (input.endsWith(".properties")) {
                var migrator = new LegacyPropertiesMigrator();
                log.info("Migrating properties from {}..", input);
                if (migrator.migrateProperties(input)) {
                    log.info("Properties migration successful");
                }
            } else {
                var output = args[1];
                var migrator = new ConfigurationYamlMigrator();
                log.info("Migrating INI from {} to {}..", input, output);
                if (migrator.migrate(input, output)) {
                    log.info("INI migration successful");
                }
            }
        } catch (Exception e) {
            throw new MigrationException("Configuration migration failed", e);
        }
    }

    private static void validateConfigArgs(String[] args) {
        if (args.length == 0) {
            logConfigUsageAndThrow("No arguments provided");
        }

        String inputFile = args[0];
        boolean isPropertiesFile = inputFile.endsWith(".properties");

        if (isPropertiesFile && args.length != 1) {
            logConfigUsageAndThrow("Properties file migration requires only input file");
        }
        if (!isPropertiesFile && args.length != 2) {
            logConfigUsageAndThrow("INI file migration requires both input and output files");
        }

        validateFilePath(inputFile, "input");
        if (!new File(inputFile).exists()) {
            throw new IllegalArgumentException("Input file does not exist: " + inputFile);
        }

        if (args.length == 2) {
            validateFilePath(args[1], "output");
        }
    }

    public static void validateFilePath(String path, String fileType) {
        try {
            var resolvedPath = Paths.get(path);
            if (resolvedPath.toFile().isDirectory()) {
                throw new IllegalArgumentException(
                        String.format("Invalid %s file path: %s is a directory", fileType, path));
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    String.format("Invalid %s file path: %s", fileType, path), e);
        }
    }

    private static void logConfigUsageAndThrow(String message) {
        log.error("Configuration Migration Usage: <input file> [output file]");
        log.error("  Properties: <file.properties>");
        log.error("  INI: <input.ini> <output.yaml>");
        throw new IllegalArgumentException(message);
    }

    private static void logPgpUsageAndThrow(String message) {
        log.error("PGP Key Migration Usage: --pgp-keys <ini-config-file>");
        log.error("  - PGP keys will be stored in Vault");
        log.error("  - Configuration migration should be done separately using MessageLogIniToDbMigrator");
        log.error("  Example: --pgp-keys /etc/xroad/conf.d/local.ini");
        throw new IllegalArgumentException(message);
    }
}

