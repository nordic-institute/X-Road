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
package org.niis.xroad.signer.common;

import ee.ria.xroad.common.crypto.SignDataPreparer;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.signer.proto.SignReq;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import static ee.ria.xroad.common.ErrorCodes.X_CANNOT_SIGN;
import static ee.ria.xroad.common.ErrorCodes.translateException;

@Slf4j
public abstract class SignReqHandler {

    public byte[] handleSign(SignReq request) {
        try {
            var signatureAlgId = SignAlgorithm.ofName(request.getSignatureAlgorithmId());
            byte[] data = SignDataPreparer.of(signatureAlgId).prepare(request.getDigest().toByteArray());

            return sign(request.getKeyId(), signatureAlgId, data);
        } catch (Exception e) {
            log.error("Error while signing with key '{}'", request.getKeyId(), e);
            throw translateException(e).withPrefix(X_CANNOT_SIGN);
        }
    }

    protected abstract byte[] sign(String keyId, SignAlgorithm signatureAlgorithmId, byte[] data)
            throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException, SignatureException, UnrecoverableKeyException,
            CertificateException, IOException, KeyStoreException;

}
