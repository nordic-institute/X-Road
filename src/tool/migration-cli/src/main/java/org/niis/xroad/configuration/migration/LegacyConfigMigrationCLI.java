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
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.migration.acme.AcmeAccountKeyMigrator;
import org.niis.xroad.migration.messagelog.MessageLogKeyMigrator;
import org.niis.xroad.migration.pgp.PgpKeyMigrator;
import org.niis.xroad.migration.signer.KeyConfMigrator;
import org.niis.xroad.migration.tokenpin.AutoLoginScriptExecutor;
import org.niis.xroad.migration.tokenpin.TokenPinMigrator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;

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
        MESSAGELOG_KEY_MAPPINGS("messagelog-key-mappings", "Migrate message log key-mapping INI file to DB"),
        ACME_ACCOUNT_KEYS("acme-account-keys", "Migrate ACME account key pairs from P12 keystore to Vault"),
        KEYCONF("keyconf", "Migrate signer key configuration to DB"),
        SIGNER_TOKEN_PINS("signer-token-pins", "Migrate signer token PINs from autologin scripts to Vault"),
        SIGNER_DEVICES("signer-devices", "Migrate signer devices.ini to DB"),
        CONFIGURATION_ANCHOR("configuration-anchor", "Migrate configuration anchor file to DB"),
        FILE_TO_DB("file-to-db", "Migrate file contents into a single DB property"),
        INI_TO_DB("ini-to-db", "Migrate INI configuration file to DB"),
        PROPERTIES_TO_DB("properties-to-db", "Migrate properties file to DB"),
        SET_PROPERTY("set-property", "Set a single property in the configuration DB"),
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
                case "messagelog-key-mappings" -> MESSAGELOG_KEY_MAPPINGS;
                case "acme-account-keys" -> ACME_ACCOUNT_KEYS;
                case "keyconf" -> KEYCONF;
                case "signer-token-pins" -> SIGNER_TOKEN_PINS;
                case "signer-devices" -> SIGNER_DEVICES;
                case "configuration-anchor" -> CONFIGURATION_ANCHOR;
                case "file-to-db" -> FILE_TO_DB;
                case "ini-to-db" -> INI_TO_DB;
                case "properties-to-db" -> PROPERTIES_TO_DB;
                case "set-property" -> SET_PROPERTY;
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
                case MESSAGELOG_KEY_MAPPINGS -> migrateMessageLogKeyMappings(shiftArgs(args));
                case ACME_ACCOUNT_KEYS -> migrateAcmeAccountKeys(shiftArgs(args));
                case KEYCONF -> migrateKeyConf(shiftArgs(args));
                case SIGNER_TOKEN_PINS -> migrateSignerTokenPins(shiftArgs(args));
                case SIGNER_DEVICES -> migrateSignerDevices(shiftArgs(args));
                case CONFIGURATION_ANCHOR -> migrateConfigurationAnchor(shiftArgs(args));
                case FILE_TO_DB -> migrateFileToDb(shiftArgs(args));
                case INI_TO_DB -> migrateIniToDb(shiftArgs(args));
                case PROPERTIES_TO_DB -> migratePropertiesToDb(shiftArgs(args));
                case SET_PROPERTY -> setProperty(shiftArgs(args));
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
                  messagelog-key-mappings        Migrate message log key-mapping INI file to DB
                  acme-account-keys              Migrate ACME account key pairs from P12 keystore to Vault
                  keyconf                        Migrate signer key configuration to DB
                  signer-token-pins              Migrate signer token PINs from autologin scripts to Vault
                  signer-devices                 Migrate signer devices.ini to DB
                  configuration-anchor           Migrate configuration anchor file to DB
                  file-to-db                     Migrate file contents into a single DB property
                  ini-to-db                      Migrate INI configuration file to DB
                  properties-to-db               Migrate properties file to DB
                  set-property                   Set a single property in the configuration DB
                  help                           Show this help message

                Configuration Migration:
                  migration-cli config <input.properties>          # Migrate properties file
                  migration-cli config <input.ini> <output.yaml>   # Migrate INI to YAML

                PGP Keys Migration:
                  migration-cli pgp-keys <ini-config-file>
                    Migrates PGP keys from GPG home directory (specified in config) to Vault.

                Message Log Database Encryption Keys Migration:
                  migration-cli messagelog-db-encryption-keys <keystore.p12> <key-id>
                    Migrates the specified database encryption key from P12 keystore to Vault.
                    Arguments:
                      <keystore.p12>  Path to PKCS12 keystore file
                      <key-id>        Key alias/ID to migrate (from messagelog-key-id config)
                    Env vars:
                      XROAD_MIGRATION_MESSAGELOG_KEYSTORE_PASSWORD  Keystore password (required; may be empty)

                Message Log Key Mappings Migration:
                  migration-cli messagelog-key-mappings <messagelog-mapping.ini> <db.properties path>
                    Migrates message log archive grouping key mappings from INI file to the configuration database.
                    Arguments:
                      <messagelog-mapping.ini> Path to message log archive encryption mapping INI file
                      <db.properties path>     Path to database properties file

                Signer keyconf migration:
                  migration-cli keyconf <keyconf path> <db.properties path>
                  Migrate signer key configuration from keyconf.xml and keys files to database.
                  Arguments:
                    <keyconf path>       Path to directory containing keyconf.xml and softtoken keys
                    <db.properties path> Path to database properties file (serverconf)

                Configuration Anchor Migration:
                  migration-cli configuration-anchor <anchor-file> <db.properties path>
                    Migrates the configuration anchor file contents into the configuration database.
                    Arguments:
                      <anchor-file>        Path to configuration anchor XML file
                      <db.properties path> Path to database properties file

                ACME Account Key Migration:
                  migration-cli acme-account-keys <acme.p12>
                    Migrates every ACME account key pair alias from a PKCS12 keystore to Vault,
                    carrying each alias's certificate expiry forward as its rotation-due timestamp.
                    The certificate itself is discarded; only its expiry date is migrated.
                    Arguments:
                      <acme.p12>  Path to PKCS12 keystore holding the ACME account key pair(s)
                    Env vars:
                      XROAD_MIGRATION_ACME_KEYSTORE_PASSWORD  Keystore password (required; may be empty)

                Signer Devices Migration:
                  migration-cli signer-devices <devices.ini> <db.properties path>
                    Migrates signer devices configuration from devices.ini to the database.
                    Arguments:
                      <devices.ini>        Path to signer devices.ini file
                      <db.properties path> Path to database properties file (signer)

                File-to-DB Migration:
                  migration-cli file-to-db <input-file> <db.properties path> <property key>
                    Migrates the entire contents of a file as a single property value into the DB.
                    Arguments:
                      <input-file>         Path to file whose contents will be stored as a property value
                      <db.properties path> Path to database properties file
                      <property key>       Property key under which the file contents will be stored

                INI to DB Migration:
                  migration-cli ini-to-db <input.ini> <db.properties path>
                    Migrates an INI properties file into the configuration database.
                    Arguments:
                      <input.ini>          Path to INI input file
                      <db.properties path> Path to database properties file

                Properties to DB Migration:
                  migration-cli properties-to-db <input.properties> <db.properties path>
                    Migrates a properties file into the configuration database.
                    Arguments:
                      <input.properties>   Path to properties input file
                      <db.properties path> Path to database properties file

                Set Property:
                  migration-cli set-property <db.properties path> <property key> <property value>
                    Sets a single property value in the configuration database.
                    Arguments:
                      <db.properties path> Path to database properties file
                      <property key>       Property key to set
                      <property value>     Property value to store

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
                  XROAD_MIGRATION_MESSAGELOG_KEYSTORE_PASSWORD=secret \\
                    migration-cli messagelog-db-encryption-keys /etc/xroad/messagelog/keystore.p12 key1
                  migration-cli messagelog-key-mappings /etc/xroad/messagelog/archive-encryption-mapping.ini /etc/xroad/db.properties
                  XROAD_MIGRATION_ACME_KEYSTORE_PASSWORD=secret \\
                    migration-cli acme-account-keys /etc/xroad/acme.p12
                  migration-cli keyconf /etc/xroad/signer /etc/xroad/db.properties
                  migration-cli configuration-anchor /etc/xroad/configuration-anchor.xml /etc/xroad/db.properties
                  migration-cli signer-devices /etc/xroad/devices.ini /etc/xroad/db.properties
                  migration-cli file-to-db /etc/xroad/conf.d/acme.yml /etc/xroad/db.properties xroad.acme proxy-ui-api
                  migration-cli ini-to-db /etc/xroad/conf.d/local.ini /etc/xroad/db.properties
                  migration-cli properties-to-db /etc/xroad/conf.d/local.properties /etc/xroad/db.properties proxy-ui-api
                  migration-cli set-property /etc/xroad/db.properties xroad.proxy.batch-signing-enabled true
                  migration-cli signer-token-pins
                  migration-cli signer-token-pins /usr/share/xroad/autologin/custom-fetch-pin.sh
                """);
    }

    private static void validateEnv() {
        new EnvironmentValidator().run();
    }

    private static void migratePgpKeys(String[] args) throws IOException {
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

        log.info("Starting PGP key migration from config: {}", configFile);
        var vaultClient = MigrationVaultClient.createAndPreflight();
        var migrator = new PgpKeyMigrator(vaultClient);
        var result = migrator.migrateFromConfig(Paths.get(configFile));
        log.info("PGP key migration result: {}", result);
    }

    private static final String MESSAGELOG_KEYSTORE_PASSWORD_ENV = "XROAD_MIGRATION_MESSAGELOG_KEYSTORE_PASSWORD";
    private static final String NO_SCOPE_ARGUMENT =
            " (rows are keyed by property_key alone; a scope argument is no longer accepted)";

    private static void migrateMessageLogKeys(String[] args) throws IOException {
        if (args.length != 2) {
            log.error("Message log database encryption key migration requires 2 arguments");
            log.error("Usage: migration-cli messagelog-db-encryption-keys <keystore.p12> <key-id>");
            log.error("  <keystore.p12>  Path to PKCS12 keystore file");
            log.error("  <key-id>        Key alias/ID to migrate (from messagelog-key-id config)");
            log.error("Env vars:");
            log.error("  {}  Keystore password (required; may be empty)", MESSAGELOG_KEYSTORE_PASSWORD_ENV);
            System.exit(1);
        }

        String keystorePath = args[0];
        String keyId = args[1];

        String password = System.getenv(MESSAGELOG_KEYSTORE_PASSWORD_ENV);
        if (password == null) {
            throw new IllegalStateException(
                    "Keystore password not provided. Export " + MESSAGELOG_KEYSTORE_PASSWORD_ENV
                            + " before running the messagelog-db-encryption-keys migration step.");
        }

        validateFilePath(keystorePath, "keystore");

        if (!new File(keystorePath).exists()) {
            throw new IllegalArgumentException("Keystore file does not exist: " + keystorePath);
        }

        log.info("Starting message log database encryption key migration");
        var vaultClient = MigrationVaultClient.createAndPreflight();
        var migrator = new MessageLogKeyMigrator(vaultClient);
        var result = migrator.migrateFromKeystore(
                Paths.get(keystorePath),
                password.toCharArray(),
                keyId
        );
        log.info("Message log encryption key migration result: {}", result);
    }

    private static final String ACME_KEYSTORE_PASSWORD_ENV = "XROAD_MIGRATION_ACME_KEYSTORE_PASSWORD";

    private static void migrateAcmeAccountKeys(String[] args) throws IOException {
        if (args.length != 1) {
            log.error("ACME account key migration requires 1 argument");
            log.error("Usage: migration-cli acme-account-keys <acme.p12>");
            log.error("  <acme.p12>  Path to PKCS12 keystore holding the ACME account key pair(s)");
            log.error("Env vars:");
            log.error("  {}  Keystore password (required; may be empty)", ACME_KEYSTORE_PASSWORD_ENV);
            System.exit(1);
        }

        String keystorePath = args[0];

        String password = System.getenv(ACME_KEYSTORE_PASSWORD_ENV);
        if (password == null) {
            throw new IllegalStateException(
                    "Keystore password not provided. Export " + ACME_KEYSTORE_PASSWORD_ENV
                            + " before running the acme-account-keys migration step.");
        }

        validateFilePath(keystorePath, "keystore");

        if (!new File(keystorePath).exists()) {
            throw new IllegalArgumentException("Keystore file does not exist: " + keystorePath);
        }

        log.info("Starting ACME account key migration");
        var vaultClient = MigrationVaultClient.createAndPreflight();
        var migrator = new AcmeAccountKeyMigrator(vaultClient);
        var result = migrator.migrateFromKeystore(Paths.get(keystorePath), password.toCharArray());
        log.info("ACME account key migration result: {}", result);
    }

    private static void migrateMessageLogKeyMappings(String[] args) {
        if (args.length != 2) {
            log.error("Message log key mappings migration requires 2 arguments");
            log.error("Usage: migration-cli messagelog-key-mappings <messagelog-mapping.ini> <db.properties path>");
            log.error("  <messagelog-mapping.ini> Path to message log archive encryption mapping INI file");
            log.error("  <db.properties path>     Path to database properties file");
            System.exit(1);
        }

        String mappingIniPath = args[0];
        String dbPropertiesPath = args[1];

        validateFilePath(mappingIniPath, "message log mapping INI");
        validateFilePath(dbPropertiesPath, "database properties");

        if (!new File(mappingIniPath).exists()) {
            log.error("Message log mapping INI file does not exist: {}", mappingIniPath);
            System.exit(1);
        }

        log.info("Starting message log key mappings migration from: {}", mappingIniPath);
        new MessageLogIniToDbMigrator().migrate(mappingIniPath, dbPropertiesPath);
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

    private static void migrateSignerDevices(String[] args) {
        if (args.length != 2) {
            log.error("Signer devices migration requires 2 arguments");
            log.error("Usage: migration-cli signer-devices <devices.ini> <db.properties path>");
            log.error("  <devices.ini>        Path to signer devices.ini file");
            log.error("  <db.properties path> Path to database properties file (signer)");
            System.exit(1);
        }

        String devicesIniPath = args[0];
        String dbPropertiesPath = args[1];

        validateFilePath(devicesIniPath, "devices.ini");
        validateFilePath(dbPropertiesPath, "database properties");

        if (!new File(devicesIniPath).exists()) {
            log.error("Signer devices file does not exist: {}", devicesIniPath);
            System.exit(1);
        }

        log.info("Starting signer devices migration from: {}", devicesIniPath);
        new DevicesIniToDbMigrator().migrate(devicesIniPath, dbPropertiesPath);
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private static void migrateConfigurationAnchor(String[] args) {
        if (args.length != 2) {
            log.error("Configuration anchor migration requires 2 arguments");
            log.error("Usage: migration-cli configuration-anchor <anchor-file> <db.properties path>");
            log.error("  <anchor-file>        Path to configuration anchor XML file");
            log.error("  <db.properties path> Path to database properties file");
            System.exit(1);
        }

        String anchorFilePath = args[0];
        String dbPropertiesPath = args[1];

        validateFilePath(anchorFilePath, "configuration anchor");
        validateFilePath(dbPropertiesPath, "database properties");

        if (!new File(anchorFilePath).exists()) {
            log.error("Configuration anchor file does not exist: {}", anchorFilePath);
            System.exit(1);
        }

        log.info("Starting configuration anchor migration from: {}", anchorFilePath);
        new ConfigurationAnchorMigrator().migrate(anchorFilePath, dbPropertiesPath);
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private static void migrateFileToDb(String[] args) {
        if (args.length != 3) {
            log.error("File-to-DB migration requires 3 arguments" + NO_SCOPE_ARGUMENT);
            log.error("Usage: migration-cli file-to-db <input-file> <db.properties path> <property key>");
            log.error("  <input-file>         Path to file whose contents will be stored as a property value");
            log.error("  <db.properties path> Path to database properties file");
            log.error("  <property key>       Property key under which the file contents will be stored");
            System.exit(1);
        }

        String inputFilePath = args[0];
        String dbPropertiesPath = args[1];
        String propertyKey = args[2];

        validateFilePath(inputFilePath, "input");
        validateFilePath(dbPropertiesPath, "database properties");

        if (StringUtils.isBlank(propertyKey)) {
            log.error("Property key cannot be empty");
            System.exit(1);
        }

        if (!new File(inputFilePath).exists()) {
            log.error("Input file does not exist: {}", inputFilePath);
            System.exit(1);
        }

        log.info("Starting file-to-db migration from: {} (property key={})", inputFilePath, propertyKey);
        new FileToDbPropertyMigrator(propertyKey).migrate(inputFilePath, dbPropertiesPath);
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private static void setProperty(String[] args) {
        if (args.length != 3) {
            log.error("set-property requires 3 arguments" + NO_SCOPE_ARGUMENT);
            log.error("Usage: migration-cli set-property <db.properties path> <property key> <property value>");
            log.error("  <db.properties path> Path to database properties file");
            log.error("  <property key>       Property key to set");
            log.error("  <property value>     Property value to store");
            System.exit(1);
        }

        String dbPropertiesPath = args[0];
        String propertyKey = args[1];
        String propertyValue = args[2];

        validateFilePath(dbPropertiesPath, "database properties");

        if (StringUtils.isAnyBlank(propertyKey, propertyValue)) {
            log.error("Property key and value cannot be empty");
            System.exit(1);
        }

        log.info("Setting property {} in DB", propertyKey);
        new SinglePropertySetter(propertyKey, propertyValue).migrate("cmdline", dbPropertiesPath);
    }

    private static void migrateIniToDb(String[] args) {
        if (args.length != 2) {
            log.error("INI to DB migration requires 2 arguments");
            log.error("Usage: migration-cli ini-to-db <input.ini> <db.properties path>");
            log.error("  <input.ini>          Path to INI input file");
            log.error("  <db.properties path> Path to database properties file");
            System.exit(1);
        }

        String iniInputPath = args[0];
        String dbPropertiesPath = args[1];

        validateFilePath(iniInputPath, "INI input");
        validateFilePath(dbPropertiesPath, "database properties");

        if (!new File(iniInputPath).exists()) {
            log.error("INI input file does not exist: {}", iniInputPath);
            System.exit(1);
        }

        log.info("Starting INI to DB migration from: {}", iniInputPath);
        new IniToDbMigrator().migrate(iniInputPath, dbPropertiesPath);
    }

    private static void migratePropertiesToDb(String[] args) {
        if (args.length != 2) {
            log.error("Properties to DB migration requires 2 arguments" + NO_SCOPE_ARGUMENT);
            log.error("Usage: migration-cli properties-to-db <input.properties> <db.properties path>");
            log.error("  <input.properties>   Path to properties input file");
            log.error("  <db.properties path> Path to database properties file");
            System.exit(1);
        }

        String inputFilePath = args[0];
        String dbPropertiesPath = args[1];

        validateFilePath(inputFilePath, "properties input");
        validateFilePath(dbPropertiesPath, "database properties");

        if (!new File(inputFilePath).exists()) {
            log.error("Properties input file does not exist: {}", inputFilePath);
            System.exit(1);
        }

        log.info("Starting properties to DB migration from: {}", inputFilePath);
        new PropertiesToDbMigrator().migrate(inputFilePath, dbPropertiesPath);
    }

    private static void migrateSignerTokenPins(String[] args) throws IOException {
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

        log.info("Starting signer token PIN migration from script: {}", scriptPath);
        var vaultClient = MigrationVaultClient.createAndPreflight();
        var executor = new AutoLoginScriptExecutor();
        var migrator = new TokenPinMigrator(vaultClient, executor);
        var result = migrator.migrateFromScript(scriptPath);
        log.info("Token PIN migration status: {}", result.status());
        log.info("Message: {}", result.message());
        if (!result.successfulTokens().isEmpty()) {
            log.info("Migrated tokens: {}", String.join(", ", result.successfulTokens()));
        }
        if (!result.skippedTokens().isEmpty()) {
            log.info("Skipped tokens (already exist): {}", String.join(", ", result.skippedTokens()));
        }
        if (!result.failedTokens().isEmpty()) {
            log.warn("Failed tokens: {}", result.failedTokens());
        }
        if (!result.isSuccessful()) {
            throw XrdRuntimeException.systemException(INTERNAL_ERROR,
                    "Signer token PIN migration did not succeed (status=%s, failed=%s)",
                    result.status(), result.failedTokens());
        }
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

