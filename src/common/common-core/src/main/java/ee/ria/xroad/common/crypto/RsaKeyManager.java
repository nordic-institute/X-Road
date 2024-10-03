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

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.crypto.identifier.KeyType;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.RSAPublicKeySpec;

public final class RsaKeyManager extends AbstractKeyManager {

    // Use no digesting algorithm, since the input data is already a digest
    private static final SignAlgorithm SIGNATURE_ALGORITHM = SignAlgorithm.ofName("NONEwithRSA");
    private static final KeyType CRYPTO_ALGORITHM = KeyType.RSA;

    RsaKeyManager() {
        super(CRYPTO_ALGORITHM);
    }

    /**
     * Generates X509 encoded public key bytes from a given modulus and
     * public exponent.
     * @param modulus the modulus
     * @param publicExponent the public exponent
     * @return generated public key bytes
     * @throws Exception if any errors occur
     */
    public byte[] generateX509PublicKey(BigInteger modulus, BigInteger publicExponent) throws Exception {
        RSAPublicKeySpec rsaPublicKeySpec = new RSAPublicKeySpec(modulus, publicExponent);
        return generateX509PublicKey(rsaPublicKeySpec);
    }

    @Override
    public SignAlgorithm getSoftwareTokenSignAlgorithm() {
        return SIGNATURE_ALGORITHM;
    }

    @Override
    public KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance(cryptoAlgorithm().name());
        keyPairGen.initialize(SystemProperties.getSignerKeyLength(), new SecureRandom());

        return keyPairGen.generateKeyPair();
    }
}
