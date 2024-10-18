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
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;

import java.security.KeyPair;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

public interface KeyManager {

    KeyAlgorithm cryptoAlgorithm();

    /**
     * Generates X509 encoded public key bytes from a given key spec.
     * @param keySPec the key spec
     * @return generated public key bytes
     * @throws Exception if any errors occur
     */
    byte[] generateX509PublicKey(KeySpec keySPec) throws InvalidKeySpecException;

    /**
     * Generates X509 encoded public key bytes from a given public key.
     * @param publicKey the public key
     * @return generated public key bytes
     * @throws Exception if any errors occur
     */
    byte[] generateX509PublicKey(PublicKey publicKey) throws InvalidKeySpecException;

    /**
     * Reads a public key from X509 encoded bytes.
     * @param encoded the data
     * @return public key read from the bytes
     * @throws Exception if any errors occur
     */
    PublicKey readX509PublicKey(byte[] encoded) throws Exception;

    /**
     * Reads a public key from X509 encoded bytes.
     * @param encodedBase64 the data as base64 encoded string
     * @return public key read from the bytes
     * @throws Exception if any errors occur
     */
    PublicKey readX509PublicKey(String encodedBase64) throws Exception;

    SignAlgorithm getSoftwareTokenSignAlgorithm();

    SignAlgorithm getSoftwareTokenKeySignAlgorithm();

    KeyPair generateKeyPair() throws Exception;
}
