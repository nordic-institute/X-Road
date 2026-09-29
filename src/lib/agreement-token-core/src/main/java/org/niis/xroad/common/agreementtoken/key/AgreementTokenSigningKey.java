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

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;

import java.util.Objects;

/**
 * One ES256 signing key pair together with the key id carried in a minted token's {@code kid} header. Key
 * ids are assigned by {@link AgreementTokenKeyProvider} and are never reused, so a token's {@code kid}
 * always resolves to the exact key it was signed with, even after rotation.
 * <p>
 * The key is a P-256 JWK. Keys served by the vault-backed provider carry the private part, which the minter
 * signs with; the verifier checks against {@link #publicKey()} alone, so a provider handing out public-only
 * keys is valid for verification and can never mint. {@link ECKey} is immutable, so the record hands out the
 * same instance without copying.
 */
public record AgreementTokenSigningKey(String keyId, ECKey keyPair) {

    public AgreementTokenSigningKey {
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("keyId must not be blank");
        }
        Objects.requireNonNull(keyPair, "keyPair must not be null");
        if (!Curve.P_256.equals(keyPair.getCurve())) {
            throw new IllegalArgumentException("keyPair must be a P-256 key, got " + keyPair.getCurve());
        }
    }

    public ECKey publicKey() {
        return keyPair.toPublicJWK();
    }
}
