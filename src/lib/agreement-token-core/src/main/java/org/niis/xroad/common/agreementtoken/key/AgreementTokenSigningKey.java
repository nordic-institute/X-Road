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
package org.niis.xroad.common.agreementtoken.key;

import java.util.Objects;

/**
 * One HMAC signing key together with the key id carried in a minted token's {@code kid} header. Key ids are
 * assigned by {@link AgreementTokenKeyProvider} and are never reused, so a token's {@code kid} always resolves
 * to the exact key it was signed with, even after rotation.
 * <p>
 * {@code secret} is defensively copied on construction and on every read: nothing outside this record can hold
 * a reference that outlives the record and mutate the key material out from under it.
 */
public record AgreementTokenSigningKey(String keyId, byte[] secret) {

    public AgreementTokenSigningKey {
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("keyId must not be blank");
        }
        Objects.requireNonNull(secret, "secret must not be null");
        if (secret.length == 0) {
            throw new IllegalArgumentException("secret must not be empty");
        }
        secret = secret.clone();
    }

    @Override
    public byte[] secret() {
        return secret.clone();
    }
}
