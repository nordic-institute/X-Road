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
package ee.ria.xroad.proxy.signedmessage;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.signature.BatchSigner;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.signature.SigningRequest;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.extern.slf4j.Slf4j;

import static ee.ria.xroad.common.ErrorCodes.X_CANNOT_CREATE_SIGNATURE;
import static ee.ria.xroad.common.ErrorCodes.translateWithPrefix;

/**
 * Signing key that is located in SSCD (secure signature creation device).
 */
@Slf4j
public class SignerSigningKey implements SigningKey {

    /** The private key ID. */
    private final String keyId;

    /** The sign mechanism name (PKCS#11) */
    private final String signMechanismName;

    /**
     * Creates a new SignerSigningKey with provided keyId.
     * @param keyId the private key ID.
     */
    public SignerSigningKey(String keyId, String signMechanismName) {
        if (keyId == null) {
            throw new IllegalArgumentException("KeyId must not be null");
        }

        if (signMechanismName == null) {
            throw new IllegalArgumentException("SignMechanismName must not be null");
        }

        this.keyId = keyId;
        this.signMechanismName = signMechanismName;
    }

    @Override
    public SignatureData calculateSignature(SigningRequest request, String digestAlgoId) throws Exception {
        String signAlgoId = CryptoUtils.getSignatureAlgorithmId(digestAlgoId, signMechanismName);

        log.trace("Calculating signature using algorithm {}", signAlgoId);

        if (SystemProperties.USE_DUMMY_SIGNATURE) {
            return new SignatureData("dymmySignatureXML", "dummyHashChainResult", "dummyHashChain");
        }

        try {
            return BatchSigner.sign(keyId, signAlgoId, request);
        } catch (Exception e) {
            throw translateWithPrefix(X_CANNOT_CREATE_SIGNATURE, e);
        }
    }
}
