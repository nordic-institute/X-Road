/*
 * The MIT License
 *
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
package org.niis.xroad.migration.pgp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.vault.VaultClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Migrates existing GPG keys to Vault.
 */
@Slf4j
@SuppressWarnings("MagicNumber")
@RequiredArgsConstructor
public final class PgpKeyMigrator {

    private final VaultClient vaultClient;
    private final MessageLogConfigReader configReader = new MessageLogConfigReader();
    private final PgpKeyValidator keyValidator = new PgpKeyValidator();
    private final GpgKeyExporter gpgExporter = new GpgKeyExporter();

    /**
     * Migrates GPG keys from configuration to Vault.
     *
     */
    public MigrationResult migrateFromConfig(Path configPath) throws IOException {
        log.info("Starting PGP key migration from config: {}", configPath);

        // Read configuration
        MessageLogConfig config = configReader.readConfig(configPath);

        // Check if encryption is enabled
        if (config.isEncryptionDisabled()) {
            log.info("Archive encryption is disabled in configuration. Skipping migration.");
            return MigrationResult.skipped("Archive encryption is not enabled");
        }

        log.info("Archive encryption enabled with grouping: {}", config.getArchiveGrouping());
        log.info("GPG home directory: {}", config.getArchiveGpgHomeDirectory());

        // Export keys from GPG home
        if (!Files.exists(config.getArchiveGpgHomeDirectory())) {
            throw new IOException("GPG home directory not found: " + config.getArchiveGpgHomeDirectory());
        }

        Path tempDir = Files.createTempDirectory("pgp-key-migration");
        try {
            Path secretKeyFile = tempDir.resolve("secret.asc");
            Path publicKeysFile = tempDir.resolve("public.asc");

            gpgExporter.exportKeys(config.getArchiveGpgHomeDirectory(), secretKeyFile, publicKeysFile);

            // Read exported keys
            String secretKeyArmored = Files.readString(secretKeyFile, StandardCharsets.UTF_8);
            String publicKeysArmored = Files.readString(publicKeysFile, StandardCharsets.UTF_8);

            // Validate keys
            var validationResult = keyValidator.validate(secretKeyArmored, publicKeysArmored);
            if (!validationResult.valid()) {
                throw new IOException("Key validation failed: " + validationResult.message());
            }

            log.info("Keys validated successfully: {}", validationResult);

            // Store keys in Vault (secure storage)
            storeKeysInVault(secretKeyArmored, publicKeysArmored);

            // Verify storage
            if (!verifyKeysInVault()) {
                throw new IOException("Keys stored but verification failed");
            }

            return MigrationResult.success(validationResult.primaryKeyId(),
                    validationResult.secretKeyCount(), validationResult.publicKeyCount());

        } finally {
            cleanupTempDirectory(tempDir);
        }
    }

    /**
     * Imports PGP keys from exported GPG files into Vault.
     *
     * @param secretKeyPath  Path to exported secret key file (.asc)
     * @param publicKeysPath Path to exported public keys file (.asc)
     */
    public MigrationResult importKeysFromFiles(Path secretKeyPath, Path publicKeysPath) throws IOException {
        log.info("Importing PGP keys from files into Vault");

        if (!Files.exists(secretKeyPath)) {
            throw new IOException("Secret key file not found: " + secretKeyPath);
        }
        if (!Files.exists(publicKeysPath)) {
            throw new IOException("Public keys file not found: " + publicKeysPath);
        }

        String secretKeyArmored = Files.readString(secretKeyPath, StandardCharsets.UTF_8);
        String publicKeysArmored = Files.readString(publicKeysPath, StandardCharsets.UTF_8);

        // Validate keys
        var validationResult = keyValidator.validate(secretKeyArmored, publicKeysArmored);
        if (!validationResult.valid()) {
            throw new IOException("Key validation failed: " + validationResult.message());
        }

        log.info("Keys validated: {}", validationResult);

        // Store in Vault
        storeKeysInVault(secretKeyArmored, publicKeysArmored);

        // Verify
        if (verifyKeysInVault()) {
            return MigrationResult.success(validationResult.primaryKeyId(),
                    validationResult.secretKeyCount(), validationResult.publicKeyCount());
        }

        throw new IOException("Keys stored but verification failed");
    }

    private void storeKeysInVault(String secretKeyArmored, String publicKeysArmored) {
        vaultClient.setMLogArchivalSigningSecretKey(secretKeyArmored);
        vaultClient.setMLogArchivalEncryptionPublicKeys(publicKeysArmored);
        log.info("Stored PGP keys in Vault");
    }

    private boolean verifyKeysInVault() {
        try {
            var secretKey = vaultClient.getMLogArchivalSigningSecretKey();
            var publicKeys = vaultClient.getMLogArchivalEncryptionPublicKeys();

            boolean keysValid = secretKey.isPresent() && !secretKey.get().isBlank()
                    && publicKeys.isPresent() && !publicKeys.get().isBlank();

            if (!keysValid) {
                log.warn("PGP keys incomplete or missing in Vault");
                return false;
            }

            log.info("PGP keys verified in Vault");
            return true;
        } catch (Exception e) {
            log.error("Failed to verify keys in Vault", e);
            return false;
        }
    }

    private void cleanupTempDirectory(Path tempDir) {
        if (tempDir == null || !Files.exists(tempDir)) {
            log.debug("Temp directory is null or doesn't exist, skipping cleanup");
            return;
        }

        try (var stream = Files.walk(tempDir)) {
            var pathsToDelete = stream
                    .sorted(java.util.Comparator.reverseOrder())
                    .toList();

            for (Path path : pathsToDelete) {
                try {
                    // Overwrite sensitive files before deletion for security
                    if (Files.isRegularFile(path) && path.toString().endsWith(".asc")) {
                        secureDeleteFile(path);
                    } else {
                        Files.deleteIfExists(path);
                    }
                } catch (IOException e) {
                    log.warn("Failed to delete temp file: {}", path, e);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to cleanup temp directory: {}", tempDir, e);
        }
    }

    /**
     * Securely deletes a file containing sensitive data by overwriting it before deletion.
     * This reduces the risk of data recovery from disk.
     */
    private void secureDeleteFile(Path file) throws IOException {
        if (!Files.exists(file)) {
            return;
        }

        try {
            long size = Files.size(file);
            // Overwrite with zeros
            byte[] zeros = new byte[(int) Math.min(size, 8192)];
            try (var out = Files.newOutputStream(file)) {
                long remaining = size;
                while (remaining > 0) {
                    int toWrite = (int) Math.min(remaining, zeros.length);
                    out.write(zeros, 0, toWrite);
                    remaining -= toWrite;
                }
                out.flush();
            }
        } catch (IOException e) {
            log.warn("Failed to securely overwrite file: {}, will attempt deletion anyway", file, e);
        } finally {
            // Always attempt to delete, even if overwrite failed
            Files.deleteIfExists(file);
        }
    }
}

