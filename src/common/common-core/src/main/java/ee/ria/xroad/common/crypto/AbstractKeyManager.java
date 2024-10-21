/*
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.common.crypto;

import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;
import ee.ria.xroad.common.crypto.identifier.Providers;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.X509EncodedKeySpec;

import static ee.ria.xroad.common.crypto.identifier.Providers.BOUNCY_CASTLE;
import static ee.ria.xroad.common.util.EncoderUtils.decodeBase64;
import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

@RequiredArgsConstructor(access = PRIVATE)
public abstract class AbstractKeyManager implements KeyManager {

    static {
        Providers.init();
    }

    /** Holds the RSA key factory instance. */
    @Getter(PROTECTED)
    public final KeyFactory keyFactory;
    public final KeyAlgorithm keyAlgorithm;

    protected AbstractKeyManager(KeyAlgorithm keyAlgorithm) {
        this.keyAlgorithm = keyAlgorithm;
        try {
            this.keyFactory = KeyFactory.getInstance(keyAlgorithm.name(), BOUNCY_CASTLE);
        } catch (Exception e) {
            throw new CryptoException("Failed to get key factory instance for : " + keyAlgorithm, e);
        }
    }

    @Override
    public KeyAlgorithm cryptoAlgorithm() {
        return keyAlgorithm;
    }


    @Override
    public byte[] generateX509PublicKey(KeySpec keySpec) throws InvalidKeySpecException {
        var publicKey = getKeyFactory().generatePublic(keySpec);
        return generateX509PublicKey(publicKey);
    }

    @Override
    public byte[] generateX509PublicKey(PublicKey publicKey) throws InvalidKeySpecException {
        var x509EncodedPublicKey = getKeyFactory().getKeySpec(publicKey, X509EncodedKeySpec.class);
        return x509EncodedPublicKey.getEncoded();
    }

    public PublicKey readX509PublicKey(byte[] encoded) throws Exception {
        var x509EncodedPublicKey = new X509EncodedKeySpec(encoded);
        return getKeyFactory().generatePublic(x509EncodedPublicKey);
    }

    @Override
    public PublicKey readX509PublicKey(String encodedBase64) throws Exception {
        return readX509PublicKey(decodeBase64(encodedBase64));
    }


}
