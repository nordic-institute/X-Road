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
package org.niis.xroad.common.vault;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Utility class for common vault data operations related to MLogDBEncryption.
 */
@Slf4j
@UtilityClass
public class MessageLogVaultDataUtils {

    /**
     * Builds the vault path for a specific encryption key ID.
     *
     * @param keyId The key identifier
     * @return The full vault path for the encryption key
     */
    public static String buildEncryptionKeyPath(String keyId) {
        return VaultClient.MLOG_DB_ENCRYPTION_SECRET_KEYS_BASE_PATH + "/" + keyId;
    }

    /**
     * Creates a secret map for storing an encryption key.
     *
     * @param base64SecretKey Base64-encoded secret key bytes
     * @return Map containing the payload key
     */
    public static Map<String, String> createEncryptionKeySecret(String base64SecretKey) {
        var secret = new HashMap<String, String>();
        secret.put(VaultClient.PAYLOAD_KEY, base64SecretKey);
        return secret;
    }

    /**
     * Retrieves all message log database encryption secret keys from Vault.
     * This method abstracts the common logic for iterating through keys and reading them.
     *
     * @param listKeysFunction   Function that lists all key IDs under the base path
     * @param readSecretFunction Function that reads a secret from a given path and returns Optional of Map
     * @return Map of keyId to base64-encoded secret keys
     */
    public static Map<String, String> getMLogDBEncryptionSecretKeys(
            Function<String, List<String>> listKeysFunction,
            Function<String, Optional<? extends Map<String, ?>>> readSecretFunction) {
        Map<String, String> keys = new HashMap<>();

        try {
            // List all keys under the base path
            List<String> keyList = listKeysFunction.apply(VaultClient.MLOG_DB_ENCRYPTION_SECRET_KEYS_BASE_PATH);
            if (keyList == null || keyList.isEmpty()) {
                return keys;
            }

            // Read each key
            for (String keyId : keyList) {
                String path = buildEncryptionKeyPath(keyId);
                readSecretFunction.apply(path).ifPresent(secret -> {
                    Object base64KeyObj = secret.get(VaultClient.PAYLOAD_KEY);
                    if (base64KeyObj != null) {
                        keys.put(keyId, base64KeyObj.toString());
                        log.debug("Loaded encryption key from Vault: {}", keyId);
                    }
                });
            }

            log.info("Loaded {} encryption key(s) from Vault", keys.size());
        } catch (Exception e) {
            log.warn("Failed to list encryption keys from Vault: {}", e.getMessage());
        }

        return keys;
    }
}
