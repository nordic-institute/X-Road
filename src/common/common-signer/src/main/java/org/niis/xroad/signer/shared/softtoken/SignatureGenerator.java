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
package org.niis.xroad.signer.shared.softtoken;

import ee.ria.xroad.common.crypto.KeyManagers;
import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;

import static ee.ria.xroad.common.crypto.identifier.Providers.BOUNCY_CASTLE;
import static org.niis.xroad.common.core.exception.ErrorCode.UNSUPPORTED_SIGN_ALGORITHM;
import static org.niis.xroad.signer.shared.SigningUtil.checkSignatureAlgorithm;

@ApplicationScoped
@RequiredArgsConstructor
public class SignatureGenerator {

    private final KeyManagers keyManagers;

    public byte[] sign(PrivateKey privateKey, SignAlgorithm signatureAlgorithmId, byte[] data)
            throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException, SignatureException {

        var keyAlgorithm = KeyAlgorithm.valueOf(privateKey.getAlgorithm());
        checkSignatureAlgorithm(signatureAlgorithmId, keyAlgorithm);

        if (!keyAlgorithm.equals(signatureAlgorithmId.algorithm())) {
            throw XrdRuntimeException.systemException(UNSUPPORTED_SIGN_ALGORITHM,
                    "Unsupported signature algorithm '%s' for key algorithm '%s'".formatted(signatureAlgorithmId.name(), keyAlgorithm));
        }

        SignAlgorithm signAlgorithm = keyManagers.getFor(keyAlgorithm).getSoftwareTokenSignAlgorithm();
        Signature signature = Signature.getInstance(signAlgorithm.name(), BOUNCY_CASTLE);
        signature.initSign(privateKey);
        signature.update(data);

        return signature.sign();
    }

}
