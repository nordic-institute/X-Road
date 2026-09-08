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
package org.niis.xroad.signer.core.tokenmanager.token;

import ee.ria.xroad.common.crypto.SignDataPreparer;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.crypto.Digests.calculateDigest;

@Slf4j
class HardwareTokenContentSigner implements ContentSigner {

    private final HardwareTokenSigner signer;
    private final ByteArrayOutputStream out;
    private final String keyId;
    private final SignAlgorithm signatureAlgorithmId;

    HardwareTokenContentSigner(HardwareTokenSigner signer, String keyId, SignAlgorithm signatureAlgorithmId) {
        this.signer = signer;
        this.keyId = keyId;
        this.signatureAlgorithmId = signatureAlgorithmId;
        out = new ByteArrayOutputStream();
    }

    @Override
    public byte[] getSignature() {
        try {
            byte[] dataToSign = out.toByteArray();
            byte[] digest = calculateDigest(signatureAlgorithmId.digest(), dataToSign);
            byte[] dataDigestToSign = SignDataPreparer.of(signatureAlgorithmId).prepare(digest);
            return signer.sign(keyId, signatureAlgorithmId, dataDigestToSign);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw translateException(e);
        }
    }

    @Override
    public OutputStream getOutputStream() {
        return out;
    }

    @Override
    public AlgorithmIdentifier getAlgorithmIdentifier() {
        return new DefaultSignatureAlgorithmIdentifierFinder().find(signatureAlgorithmId.name());
    }

}
