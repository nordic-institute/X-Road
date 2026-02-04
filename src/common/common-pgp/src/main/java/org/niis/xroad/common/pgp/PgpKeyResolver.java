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
package org.niis.xroad.common.pgp;

import lombok.RequiredArgsConstructor;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Resolves recipient public keys from key IDs or member identifiers.
 * Supports both direct key ID resolution and member-level key mapping from Vault.
 */
@RequiredArgsConstructor
public final class PgpKeyResolver {

    private final PgpKeyManager keyManager;

    /**
     * Resolves recipient keys. If no specific keys provided, uses signing key (self-encryption).
     *
     * @param recipientKeyIds Set of recipient key IDs
     * @return List of resolved public keys
     */
    public List<PGPPublicKey> resolveRecipients(Set<String> recipientKeyIds)
            throws PGPException {

        if (recipientKeyIds == null || recipientKeyIds.isEmpty()) {
            // Self-encryption using signing key
            return List.of(keyManager.getSigningKeyPair().publicKey());
        }

        List<PGPPublicKey> recipients = new ArrayList<>(recipientKeyIds.size());
        List<String> missingKeys = new ArrayList<>();

        for (String keyId : recipientKeyIds) {
            var pubKey = keyManager.getPublicKey(keyId);
            if (pubKey.isPresent()) {
                recipients.add(pubKey.get());
            } else {
                missingKeys.add(keyId);
            }
        }

        if (!missingKeys.isEmpty()) {
            throw new PGPException("Public keys not found for IDs: " + String.join(", ", missingKeys));
        }

        return recipients;
    }
}

