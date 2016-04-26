/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import lombok.extern.slf4j.Slf4j;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.signature.BatchSigner;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.signature.SigningRequest;

import static ee.ria.xroad.common.ErrorCodes.X_CANNOT_CREATE_SIGNATURE;
import static ee.ria.xroad.common.ErrorCodes.translateWithPrefix;

/**
 * Signing key that is located in SSCD (secure signature creation device).
 */
@Slf4j
public class SignerSigningKey implements SigningKey {

    /** The private key ID. */
    private final String keyId;

    /**
     * Creates a new SignerSigningKey with provided keyId.
     * @param keyId the private key ID.
     */
    public SignerSigningKey(String keyId) {
        if (keyId == null) {
            throw new IllegalArgumentException("KeyId is must not be null");
        }

        this.keyId = keyId;
    }

    @Override
    public SignatureData calculateSignature(SigningRequest request,
            String algorithmId) throws Exception {
        log.trace("Calculating signature using algorithm {}", algorithmId);

        if (SystemProperties.USE_DUMMY_SIGNATURE) {
            return new SignatureData("dymmySignatureXML",
                    "dummyHashChainResult", "dummyHashChain");
        }

        try {
            return BatchSigner.sign(keyId, algorithmId, request);
        } catch (Exception e) {
            throw translateWithPrefix(X_CANNOT_CREATE_SIGNATURE, e);
        }
    }
}
