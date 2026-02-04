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
import org.niis.xroad.migration.signer.KeyConfMigrator;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Unified CLI for X-Road migrations.
 * Supports:
 * - Configuration migration (INI/properties to DB)
 * - PGP key migration (GPG to Vault)
 * - Message log encryption key migration (P12 to Vault)
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LegacyConfigMigrationCLI {

    private enum Command {
        VALIDATE("validate", "Check for probable config migration issues"),
        CONFIG("config", "Migrate configuration files (INI/properties to DB)"),
        PGP_KEYS("pgp-keys", "Migrate PGP keys from GPG to Vault"),
        MESSAGELOG_DB_ENCRYPTION_KEYS("messagelog-db-encryption-keys", "Migrate message log database encryption keys from P12 to Vault"),
        KEYCONF("keyconf", "Migrate signer key configuration to DB"),
        SIGNER_TOKEN_PINS("signer-token-pins", "Migrate signer token PINs from autologin scripts to Vault"),
        HELP("help", "Show this help message");

        private final String name;
        private final String description;

        Command(String name, String description) {
            this.name = name;
            this.description = description;
        }

        static Command fromString(String value) {
            return switch (value) {
                case "validate" -> VALIDATE;
                case "config" -> CONFIG;
                case "pgp-keys" -> PGP_KEYS;
                case "messagelog-db-encryption-keys" -> MESSAGELOG_DB_ENCRYPTION_KEYS;
                case "keyconf" -> KEYCONF;
                case "signer-token-pins" -> SIGNER_TOKEN_PINS;
                case "help", "-h", "--help" -> HELP;
                default -> null;
            };
        }
    }

    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                showHelp();
                return;
            }

            Command command = Command.fromString(args[0]);
            if (command == null) {
                log.error("Unknown command: {}", args[0]);
                showHelp();
                System.exit(1);
            }

            switch (command) {
                case VALIDATE -> validateEnv();
                case CONFIG -> migrateConfiguration(shiftArgs(args));
                case PGP_KEYS -> migratePgpKeys(shiftArgs(args));
                case MESSAGELOG_DB_ENCRYPTION_KEYS -> migrateMessageLogKeys(shiftArgs(args));
                case KEYCONF -> migrateKeyConf(shiftArgs(args));
                case SIGNER_TOKEN_PINS -> migrateSignerTokenPins(shiftArgs(args));
                default -> showHelp();
            }
        } catch (Exception e) {
            log.error("Migration failed: {}", e.getMessage(), e);
            throw new MigrationException("Migration failed", e);
        }
    }

    private static String[] shiftArgs(String[] args) {
        if (args.length <= 1) {
            return new String[0];
        }
        String[] shifted = new String[args.length - 1];
        System.arraycopy(args, 1, shifted, 0, args.length - 1);
        return shifted;
    }

    private static void showHelp() {
        System.out.println("""
                X-Road Migration Tool

                Usage: migration-cli <command> [options]

                Commands:
                  validate                       Check for probable migration issues
                  config                         Migrate configuration files (INI/properties to DB)
                  pgp-keys                       Migrate PGP keys from GPG to Vault
                  messagelog-db-encryption-keys  Migrate message log database encryption keys from P12 to Vault
                  keyconf                        Migrate signer key configuration to DB
                  signer-token-pins              Migrate signer token PINs from autologin scripts to Vault
                  help                           Show this help message

                Configuration Migration:
                  migration-cli config <input.properties>          # Migrate properties file
                  migration-cli config <input.ini> <output.yaml>   # Migrate INI to YAML

                PGP Keys Migration:
                  migration-cli pgp-keys <ini-config-file>
                    Migrates PGP keys from GPG home directory (specified in config) to Vault.

                Message Log Database Encryption Keys Migration:
                  migration-cli messagelog-db-encryption-keys <keystore.p12> <password> <key-id>
                    Migrates the specified database encryption key from P12 keystore to Vault.
                    Arguments:
                      <keystore.p12>  Path to PKCS12 keystore file
                      <password>      Keystore password
                      <key-id>        Key alias/ID to migrate (from messagelog-key-id config)

                Signer keyconf migration:
                  migration-cli keyconf <keyconf path> <db.properties path>
                  Migrate signer key configuration from keyconf.xml and keys files to database.
                  Arguments:
                    <keyconf path>       Path to directory containing keyconf.xml and softtoken keys
                    <db.properties path> Path to database properties file (serverconf)

                Signer Token PINs Migration:
                  migration-cli signer-token-pins [<script-path>]
                    Migrates token PINs from autologin scripts to Vault.
                    Arguments:
                      <script-path>    Optional path to fetch-pin script
                                      If not provided, auto-selects:
                                        1. /usr/share/xroad/autologin/custom-fetch-pin.sh (preferred)
                                        2. /usr/share/xroad/autologin/default-fetch-pin.sh (fallback)
                    Notes:
                      - Requires Vault to be configured and accessible
                      - Existing PINs in Vault are preserved (not overwritten)
                      - Exit code 127 from script is treated as non-fatal (no PINs to migrate)

                Examples:
                  migration-cli config /etc/xroad/conf.d/local.ini /etc/xroad/conf.d/local.yaml
                  migration-cli pgp-keys /etc/xroad/conf.d/local.ini
                  migration-cli messagelog-db-encryption-keys /etc/xroad/messagelog/keystore.p12 secret key1
                  migration-cli keyconf /etc/xroad/signer /etc/xroad/db.properties
                  migration-cli signer-token-pins
                  migration-cli signer-token-pins /usr/share/xroad/autologin/custom-fetch-pin.sh
                """);
    }

    private static void validateEnv() {
        new EnvironmentValidator().run();
    }

    private static void migratePgpKeys(String[] args) {
        if (args.length < 1) {
            log.error("PGP key migration requires configuration file");
            log.error("Usage: migration-cli pgp-keys <ini-config-file>");
            System.exit(1);
        }

        String configFile = args[0];

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
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private static void migrateMessageLogKeys(String[] args) {
        if (args.length < 3) {
            log.error("Message log database encryption key migration requires 3 arguments");
            log.error("Usage: migration-cli messagelog-db-encryption-keys <keystore.p12> <password> <key-id>");
            log.error("  <keystore.p12>  Path to PKCS12 keystore file");
            log.error("  <password>      Keystore password");
            log.error("  <key-id>        Key alias/ID to migrate (from messagelog-key-id config)");
            System.exit(1);
        }

        String keystorePath = args[0];
        String password = args[1];
        String keyId = args[2];

        validateFilePath(keystorePath, "keystore");

        if (!new File(keystorePath).exists()) {
            throw new IllegalArgumentException("Keystore file does not exist: " + keystorePath);
        }

        log.info("Starting message log database encryption key migration");
        log.info("  Keystore: {}", keystorePath);
        log.info("  Key ID: {}", keyId);

        log.warn("Message log database encryption key migration requires Vault configuration");
        log.info("Keystore file validated: {}", keystorePath);
        log.info("To complete migration, ensure Vault is configured and accessible");

        //TODO provide vault configuration

        // Note: Message log database encryption key migration requires Vault configuration
        // Example of how this would work with proper vault client:
        // VaultClient vaultClient = createVaultClient();
        // MessageLogKeyMigrator migrator = new MessageLogKeyMigrator(vaultClient);
        // MessageLogKeyMigrationResult result = migrator.migrateFromKeystore(
        //     Paths.get(keystorePath),
        //     password.toCharArray(),
        //     keyId
        // );
        // log.info("Migration result: {}", result);
        // log.info("Key stored in Vault");
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private static void migrateKeyConf(String[] args) {
        if (args.length != 2) {
            log.error("Signer keyconf migration requires 2 arguments");
            log.error("Usage: migration-cli keyconf <keyconf path> <db.properties path>");
            log.error("  <keyconf path>       Path to directory containing keyconf.xml and softtoken keys");
            log.error("  <db.properties path> Path to database properties file (serverconf)");
            System.exit(1);
        }

        String keyconfDir = args[0];
        String dbPropertiesPath = args[1];

        validateFilePath(dbPropertiesPath, "database properties");
        Path keyconfPath = Paths.get(keyconfDir);
        if (!(keyconfPath.toFile().exists() && keyconfPath.toFile().isDirectory())) {
            log.error("Keyconf directory does not exist: {}", keyconfDir);
            System.exit(1);
        }

        KeyConfMigrator keyConfMigrator = new KeyConfMigrator();
        try {
            keyConfMigrator.migrate(keyconfDir, dbPropertiesPath);
        } catch (Exception e) {
            log.error("Error while migrating Signer keyconf", e);
        }

    }

    private static void migrateSignerTokenPins(String[] args) {
        // Determine script path
        Path scriptPath;
        if (args.length > 0) {
            scriptPath = Path.of(args[0]);
        } else {
            Path customScript = Path.of("/usr/share/xroad/autologin/custom-fetch-pin.sh");
            Path defaultScript = Path.of("/usr/share/xroad/autologin/default-fetch-pin.sh");
            scriptPath = Files.exists(customScript) ? customScript : defaultScript;
        }

        log.info("Using fetch-pin script: {}", scriptPath);

        if (!Files.exists(scriptPath)) {
            log.error("Fetch-pin script not found: {}", scriptPath);
            log.error("Ensure xroad-autologin package is installed or provide an explicit script path");
            System.exit(1);
        }

        //TODO provide vault configuration

        // Example of how this would work with proper vault client:
        // VaultClient vaultClient = createVaultClient();
        // AutoLoginScriptExecutor executor = new AutoLoginScriptExecutor();
        // TokenPinMigrator migrator = new TokenPinMigrator(vaultClient, executor);
        //
        // TokenPinMigrationResult result = migrator.migrateFromScript(scriptPath);
        //
        // System.out.println("Migration Status: " + result.status());
        // System.out.println("Message: " + result.message());
        //
        // if (!result.successfulTokens().isEmpty()) {
        //     System.out.println("Migrated tokens: " + String.join(", ", result.successfulTokens()));
        // }
        // if (!result.skippedTokens().isEmpty()) {
        //     System.out.println("Skipped tokens (already exist): " + String.join(", ", result.skippedTokens()));
        // }
        // if (!result.failedTokens().isEmpty()) {
        //     System.out.println("Failed tokens:");
        //     result.failedTokens().forEach((token, error) ->
        //         System.out.println("  - " + token + ": " + error));
        // }
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
            log.error("Configuration migration requires input file");
            log.error("Usage:");
            log.error("  migration-cli config <input.properties>          # Migrate properties");
            log.error("  migration-cli config <input.ini> <output.yaml>   # Migrate INI to YAML");
            System.exit(1);
        }

        String inputFile = args[0];
        boolean isPropertiesFile = inputFile.endsWith(".properties");

        if (isPropertiesFile && args.length != 1) {
            log.error("Properties file migration requires only input file");
            log.error("Usage: migration-cli config <input.properties>");
            System.exit(1);
        }
        if (!isPropertiesFile && args.length != 2) {
            log.error("INI file migration requires both input and output files");
            log.error("Usage: migration-cli config <input.ini> <output.yaml>");
            System.exit(1);
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
}

