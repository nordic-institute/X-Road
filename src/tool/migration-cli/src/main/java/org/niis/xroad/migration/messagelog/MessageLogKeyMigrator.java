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
package org.niis.xroad.migration.messagelog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.vault.VaultClient;

import javax.crypto.SecretKey;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Base64;
import java.util.Optional;

/**
 * Migrates message log database encryption keys from PKCS12 keystore to Vault.
 */
@Slf4j
@RequiredArgsConstructor
public final class MessageLogKeyMigrator {

    private final VaultClient vaultClient;

    /**
     * Migrates the configured encryption key from P12 keystore to Vault.
     * Only migrates the single key specified by keyId (from messagelog-key-id config).
     *
     * @param keystorePath     Path to the PKCS12 keystore file
     * @param keystorePassword Password for the keystore
     * @param keyId            The key ID/alias to migrate (from messagelog-key-id configuration)
     * @return Migration result with details
     * @throws IOException If reading the keystore fails or key not found
     */
    public MessageLogKeyMigrationResult migrateFromKeystore(Path keystorePath, char[] keystorePassword, String keyId)
            throws IOException {
        log.info("Starting message log encryption key migration from keystore: {}", keystorePath);
        log.info("Migrating key with ID: {}", keyId);

        if (!Files.exists(keystorePath)) {
            throw new IOException("Keystore file not found: " + keystorePath);
        }

        if (keyId == null || keyId.isBlank()) {
            throw new IOException("Key ID is required (from messagelog-key-id configuration)");
        }

        try {
            // Extract the specific key from P12 keystore
            var secretKeyBytesOpt = extractKeyFromKeystore(keystorePath, keystorePassword, keyId);

            if (secretKeyBytesOpt.isEmpty()) {
                throw new IOException("Key with ID '" + keyId + "' not found in keystore");
            }

            log.info("Extracted secret key with ID: {}", keyId);

            // Convert to Base64
            String base64Key = Base64.getEncoder().encodeToString(secretKeyBytesOpt.get());

            // Store in Vault with keyId
            storeKeyInVault(keyId, base64Key);

            // Verify storage
            if (!verifyKeyInVault(keyId)) {
                throw new IOException("Key stored but verification failed");
            }

            return MessageLogKeyMigrationResult.success(keyId, 1);

        } catch (Exception e) {
            log.error("Failed to migrate message log encryption key", e);
            throw new IOException("Migration failed: " + e.getMessage(), e);
        } finally {
            // Clear sensitive data
            if (keystorePassword != null) {
                java.util.Arrays.fill(keystorePassword, '\0');
            }
        }
    }

    /**
     * Extracts a specific secret key from PKCS12 keystore.
     *
     * @param keystorePath     Path to the keystore
     * @param keystorePassword Password for the keystore
     * @param keyId            The key ID/alias to extract
     * @return Raw key bytes, or null if not found
     */
    private Optional<byte[]> extractKeyFromKeystore(Path keystorePath, char[] keystorePassword, String keyId)
            throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException {
        KeyStore keyStore = KeyStore.getInstance("pkcs12");
        try (InputStream is = Files.newInputStream(keystorePath)) {
            keyStore.load(is, keystorePassword);
        }

        if (!keyStore.containsAlias(keyId)) {
            log.error("Key with alias '{}' not found in keystore", keyId);
            return Optional.empty();
        }

        Key key = keyStore.getKey(keyId, keystorePassword);

        if (!(key instanceof SecretKey && "RAW".equalsIgnoreCase(key.getFormat()) && key.getEncoded() != null)) {
            throw new IllegalStateException(
                    "Keystore entry '" + keyId + "' is not a secret key with raw encoding. "
                            + "Expected: SecretKey with RAW format. Found: " + (key != null ? key.getClass().getName() : "null"));
        }

        byte[] keyBytes = key.getEncoded();
        log.debug("Extracted secret key with ID: {}, size: {} bytes", keyId, keyBytes.length);
        return Optional.of(keyBytes);
    }

    /**
     * Stores the key in Vault using the VaultClient.
     *
     * @param keyId           Key ID/alias
     * @param base64SecretKey Base64-encoded secret key
     */
    private void storeKeyInVault(String keyId, String base64SecretKey) {
        vaultClient.setMLogDBEncryptionSecretKey(keyId, base64SecretKey);
        log.info("Stored message log encryption key in Vault with ID: {}", keyId);
    }

    /**
     * Verifies that key was successfully stored in Vault.
     *
     * @param keyId Key ID to verify
     * @return true if verification succeeds, false otherwise
     */
    private boolean verifyKeyInVault(String keyId) {
        try {
            var allKeys = vaultClient.getMLogDBEncryptionSecretKeys();

            if (allKeys.isEmpty() || !allKeys.containsKey(keyId)) {
                log.warn("Message log encryption key with ID '{}' is missing in Vault", keyId);
                return false;
            }

            String storedKey = allKeys.get(keyId);
            if (storedKey == null || storedKey.isBlank()) {
                log.warn("Message log encryption key with ID '{}' is empty in Vault", keyId);
                return false;
            }

            // Verify it's valid base64
            try {
                Base64.getDecoder().decode(storedKey);
            } catch (IllegalArgumentException e) {
                log.warn("Stored data for key '{}' is not valid base64", keyId);
                return false;
            }

            log.info("Message log encryption key with ID '{}' verified in Vault", keyId);
            return true;
        } catch (Exception e) {
            log.error("Failed to verify key in Vault", e);
            return false;
        }
    }
}

