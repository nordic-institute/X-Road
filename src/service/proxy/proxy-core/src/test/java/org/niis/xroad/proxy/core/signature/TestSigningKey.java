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
package org.niis.xroad.proxy.core.signature;

import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignMechanism;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.signature.SigningRequest;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.proxy.core.signedmessage.SigningKey;

import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;

/**
 * Signing key that is located in PKCS12 key store.
 */
@Slf4j
public class TestSigningKey implements SigningKey {
    private static final SignMechanism SIGNING_MECHANISM_NAME = SignMechanism.CKM_RSA_PKCS;

    /** The private key. */
    private final PrivateKey key;

    /**
     * Creates a new Pkcs12SigningKey with provided key.
     * @param key the private key.
     */
    public TestSigningKey(PrivateKey key) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        this.key = key;
    }

    @Override
    public SignatureData calculateSignature(SigningRequest request, DigestAlgorithm digestAlgoId) throws Exception {
        log.debug("calculateSignature({}, {})", request, digestAlgoId);

        SignatureCtx ctx = new SignatureCtx(getSignatureAlgorithmId(digestAlgoId));
        ctx.add(request);

        byte[] tbsData = ctx.getDataToBeSigned();
        byte[] signatureValue = sign(ctx.getSignatureAlgorithmId(), tbsData);

        String signatureXML = ctx.createSignatureXml(signatureValue);

        return ctx.createSignatureData(signatureXML, 0);
    }

    private byte[] sign(SignAlgorithm signatureAlgorithmId, byte[] data) throws Exception {
        Signature signature = Signature.getInstance(signatureAlgorithmId.name());

        signature.initSign(key);
        signature.update(data);

        return signature.sign();
    }

    private static SignAlgorithm getSignatureAlgorithmId(DigestAlgorithm digestAlgorithmId) throws NoSuchAlgorithmException {
        return SignAlgorithm.ofDigestAndMechanism(digestAlgorithmId, SIGNING_MECHANISM_NAME);
    }
}
