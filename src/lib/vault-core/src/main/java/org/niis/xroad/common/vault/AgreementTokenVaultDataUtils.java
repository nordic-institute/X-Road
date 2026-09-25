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
 * Utility class for the vault-backed operations shared by every {@link VaultClient} implementation for the
 * agreement-token signing key, mirroring {@link MessageLogVaultDataUtils}'s per-key-id listing shape.
 */
@Slf4j
@UtilityClass
public class AgreementTokenVaultDataUtils {

    public static String buildSigningKeyPath(String keyId) {
        return VaultClient.AGREEMENT_TOKEN_SIGNING_KEYS_BASE_PATH + "/" + keyId;
    }

    public static Map<String, String> createSigningKeySecret(String base64Secret) {
        var secret = new HashMap<String, String>();
        secret.put(VaultClient.PAYLOAD_KEY, base64Secret);
        return secret;
    }

    /**
     * Retrieves every agreement-token signing key from Vault, keyed by key id. Deliberately does not catch
     * anything: a caller bootstraps a new key whenever this returns an empty map, so a listing or read
     * failure must propagate as a thrown exception — never come back disguised as "no keys yet" — or a
     * transient outage would make every replica mint its own key.
     *
     * @param listKeysFunction   function that lists all key ids under the base path; a genuine "nothing
     *                           written yet" must return an empty list, not throw
     * @param readSecretFunction function that reads a secret from a given path
     * @return map of key id to base64-encoded HMAC secret; empty only when the listing itself succeeded and
     *         found no keys
     */
    public static Map<String, String> getAgreementTokenSigningKeys(
            Function<String, List<String>> listKeysFunction,
            Function<String, Optional<? extends Map<String, ?>>> readSecretFunction) {
        Map<String, String> keys = new HashMap<>();

        List<String> keyList = listKeysFunction.apply(VaultClient.AGREEMENT_TOKEN_SIGNING_KEYS_BASE_PATH);
        if (keyList == null || keyList.isEmpty()) {
            return keys;
        }

        for (String keyId : keyList) {
            String path = buildSigningKeyPath(keyId);
            readSecretFunction.apply(path).ifPresent(secret -> {
                Object base64SecretObj = secret.get(VaultClient.PAYLOAD_KEY);
                if (base64SecretObj != null) {
                    keys.put(keyId, base64SecretObj.toString());
                    log.debug("Loaded agreement-token signing key from Vault: {}", keyId);
                }
            });
        }

        log.info("Loaded {} agreement-token signing key(s) from Vault", keys.size());
        return keys;
    }
}
