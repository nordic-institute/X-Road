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
package ee.ria.xroad.common.signature;

import java.security.PrivateKey;
import java.security.Signature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.ria.xroad.proxy.signedmessage.SigningKey;

/**
 * Signing key that is located in PKCS12 key store.
 */
public class TestSigningKey implements SigningKey {

    private static final Logger LOG =
            LoggerFactory.getLogger(TestSigningKey.class);

    /** The private key. */
    private PrivateKey key;

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
    public SignatureData calculateSignature(SigningRequest request,
            String signatureAlgorithmId) throws Exception {
        LOG.debug("calculateSignature({})", request);

        SignatureCtx ctx = new SignatureCtx(signatureAlgorithmId);
        ctx.add(request);

        byte[] tbsData = ctx.getDataToBeSigned();
        byte[] signatureValue = sign(ctx.getSignatureAlgorithmId(), tbsData);

        String signatureXML = ctx.createSignatureXml(signatureValue);
        return ctx.createSignatureData(signatureXML, 0);
    }

    protected byte[] sign(String signatureAlgorithmId, byte[] data)
            throws Exception {
        Signature signature = Signature.getInstance(signatureAlgorithmId);

        signature.initSign(key);
        signature.update(data);

        return signature.sign();
    }
}
