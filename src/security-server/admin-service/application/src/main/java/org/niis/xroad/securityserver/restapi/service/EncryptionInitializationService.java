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
package org.niis.xroad.securityserver.restapi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.pgp.PgpKeyGenerator;
import org.niis.xroad.common.vault.VaultClient;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Service for initializing encryption functionality.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EncryptionInitializationService {
    private static final String DEFAULT_DB_ENCRYPTION_KEY_ID = "default";
    private static final int MASTER_KEY_SIZE_BYTES = 32; // 256-bit master key

    private final VaultClient vaultClient;
    private final PgpKeyGenerator pgpGenerator = new PgpKeyGenerator();

    public void initializeMessageLogArchivalEncryption(String securityServerId) {
        var result = pgpGenerator.generate(securityServerId);

        vaultClient.setMLogArchivalSigningSecretKey(result.secretData());
    }

    /**
     * Initializes message log database encryption key.
     * Generates a new random AES key and stores it in Vault with the default key ID.
     * If keys already exist, logs a warning and does not overwrite them.
     */
    public void initializeMessageLogDatabaseEncryption() {
        log.info("Initializing message log database encryption key with ID: {}", DEFAULT_DB_ENCRYPTION_KEY_ID);

        var existingKeys = vaultClient.getMLogDBEncryptionSecretKeys();
        if (!existingKeys.isEmpty()) {
            log.warn("Message log database encryption keys already exist in Vault. Found {} key(s): {}. "
                            + "Skipping initialization to avoid overwriting existing keys.",
                    existingKeys.size(), existingKeys.keySet());
            return;
        }

        // Generate a new random 256-bit (32-byte) master key
        SecureRandom secureRandom = new SecureRandom();
        byte[] secretKey = new byte[MASTER_KEY_SIZE_BYTES];
        secureRandom.nextBytes(secretKey);

        var base64SecretKey = Base64.getEncoder().encodeToString(secretKey);

        vaultClient.setMLogDBEncryptionSecretKey(DEFAULT_DB_ENCRYPTION_KEY_ID, base64SecretKey);
        log.info("Successfully initialized message log database encryption master key (256-bit) with ID: {}", DEFAULT_DB_ENCRYPTION_KEY_ID);
    }
}
