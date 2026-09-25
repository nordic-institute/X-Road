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
package org.niis.xroad.common.agreementtoken;

import org.niis.xroad.common.agreementtoken.key.AgreementTokenKeyProvider;
import org.niis.xroad.common.agreementtoken.key.AgreementTokenSigningKey;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A plain in-memory {@link AgreementTokenKeyProvider} for exercising {@link AgreementTokenMinter} and
 * {@link AgreementTokenVerifier} without a real or mocked {@code VaultClient} — those two classes only ever
 * depend on the {@link AgreementTokenKeyProvider} interface, so a minimal fake proves that boundary.
 */
final class InMemoryAgreementTokenKeyProvider implements AgreementTokenKeyProvider {

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, AgreementTokenSigningKey> keysById = new LinkedHashMap<>();
    private String activeKeyId;

    AgreementTokenSigningKey addKey(String keyId, byte[] secret) {
        var key = new AgreementTokenSigningKey(keyId, secret);
        keysById.put(keyId, key);
        activeKeyId = keyId;
        return key;
    }

    @Override
    public AgreementTokenSigningKey activeKey() {
        if (activeKeyId == null) {
            throw XrdRuntimeException.systemException(ErrorCode.AGREEMENT_TOKEN_KEY_NOT_AVAILABLE)
                    .details("No agreement-token signing key is available")
                    .build();
        }
        return keysById.get(activeKeyId);
    }

    @Override
    public Optional<AgreementTokenSigningKey> keyById(String keyId) {
        return Optional.ofNullable(keysById.get(keyId));
    }

    @Override
    public AgreementTokenSigningKey rotate() {
        var secret = new byte[32];
        secureRandom.nextBytes(secret);
        return addKey(String.valueOf(keysById.size() + 1), secret);
    }

    @Override
    public void refresh() {
        // Everything is already in memory; nothing to reload.
    }
}
