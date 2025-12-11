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
package org.niis.xroad.signer.softtoken;

import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.signer.common.SignReqHandler;
import org.niis.xroad.signer.common.softtoken.SignatureGenerator;
import org.niis.xroad.signer.softtoken.sync.CachedKeyInfo;
import org.niis.xroad.signer.softtoken.sync.SoftwareTokenKeyCache;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;

import static org.niis.xroad.common.core.exception.ErrorCode.KEY_NOT_AVAILABLE;
import static org.niis.xroad.common.core.exception.ErrorCode.TOKEN_NOT_ACTIVE;

/**
 * Handles signing requests using synchronized software token keys.
 * <p>
 * This implementation retrieves keys from the synchronized cache and validates
 * their availability status before performing signing operations.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class SoftTokenSignWorker extends SignReqHandler {

    private final SoftwareTokenKeyCache keyCache;
    private final SignatureGenerator signatureGenerator;

    @Override
    protected byte[] sign(String keyId, SignAlgorithm signatureAlgorithmId, byte[] data)
            throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException, SignatureException {
        log.trace("sign({}, {})", keyId, signatureAlgorithmId);

        final CachedKeyInfo cachedKey = keyCache.getKey(keyId)
                .orElseThrow(() -> XrdRuntimeException.systemException(
                        ErrorCode.KEY_NOT_FOUND, "Key '%s' not found in cache".formatted(keyId)));

        if (!cachedKey.tokenActive()) {
            throw XrdRuntimeException.systemException(TOKEN_NOT_ACTIVE, "Token not active for key '%s'".formatted(cachedKey.keyId()));
        }

        if (!cachedKey.keyAvailable()) {
            throw XrdRuntimeException.systemException(KEY_NOT_AVAILABLE, "Key '%s' not available".formatted(keyId));
        }

        return signatureGenerator.sign(cachedKey.privateKey(), signatureAlgorithmId, data);
    }
}
