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
package org.niis.xroad.signer.softtoken.sync;

import java.security.PrivateKey;
import java.util.Arrays;

/**
 * In-memory representation of a synchronized software token key in the softtoken-signer service.
 * <p>
 * This record holds the decrypted private key and metadata required for signing operations,
 * including token and key availability status from the signer service.
 */
public record CachedKeyInfo(
        String keyId,
        PrivateKey privateKey,
        boolean tokenActive,
        boolean keyAvailable,
        String keyLabel,
        String signMechanism
) {
    /**
     * Checks if this cached key is equivalent to another, excluding the lastSynced timestamp.
     * This is used to determine if a key has actually changed during synchronization.
     *
     * @param other the other CachedKeyInfo to compare with
     * @return true if all fields except lastSynced are equal, false otherwise
     */
    public boolean equals(CachedKeyInfo other) {
        if (other == null) {
            return false;
        }
        return keyId.equals(other.keyId())
                && Arrays.equals(privateKey.getEncoded(), other.privateKey().getEncoded())
                && tokenActive == other.tokenActive()
                && keyAvailable == other.keyAvailable()
                && keyLabel.equals(other.keyLabel())
                && signMechanism.equals(other.signMechanism());
    }
}
