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
package org.niis.xroad.signer.core.tokenmanager.token.helper;

import ee.ria.xroad.common.crypto.CryptoException;
import ee.ria.xroad.common.crypto.KeyManagers;

import iaik.pkcs.pkcs11.Mechanism;
import iaik.pkcs.pkcs11.Session;
import iaik.pkcs.pkcs11.TokenException;
import iaik.pkcs.pkcs11.objects.KeyPair;
import iaik.pkcs.pkcs11.objects.PublicKey;
import iaik.pkcs.pkcs11.objects.RSAPrivateKey;
import iaik.pkcs.pkcs11.objects.RSAPublicKey;
import iaik.pkcs.pkcs11.wrapper.PKCS11Constants;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.signer.core.config.SignerProperties;
import org.niis.xroad.signer.core.tokenmanager.module.PrivKeyAttributes;
import org.niis.xroad.signer.core.tokenmanager.module.PubKeyAttributes;
import org.niis.xroad.signer.core.util.SignerUtil;

import java.math.BigInteger;
import java.security.spec.InvalidKeySpecException;

@RequiredArgsConstructor
@ApplicationScoped
public final class RsaKeyPairHelper extends AbstractKeyPairBuilder<RSAPublicKey, RSAPrivateKey> implements KeyPairHelper {

    private static final byte[] PUBLIC_EXPONENT_BYTES = {0x01, 0x00, 0x01}; // 2^16 + 1
    private static final Mechanism KEY_PAIR_GEN_MECHANISM = Mechanism.get(PKCS11Constants.CKM_RSA_PKCS_KEY_PAIR_GEN);

    private final SignerProperties signerProperties;
    private final KeyManagers keyManagers;

    @Override
    public KeyPair createKeypair(
            Session activeSession,
            String keyLabel,
            PubKeyAttributes pubKeyAttributes,
            PrivKeyAttributes privKeyAttributes) {
        byte[] id = SignerUtil.generateId();
        // XXX maybe use: byte[] id = activeSession.generateRandom(RANDOM_ID_LENGTH);
        try {
            return activeSession.generateKeyPair(
                    KEY_PAIR_GEN_MECHANISM,
                    buildPublicKeyTemplate(id, keyLabel, pubKeyAttributes),
                    buildPrivateKeyTemplate(id, keyLabel, privKeyAttributes));
        } catch (TokenException e) {
            throw XrdRuntimeException.systemException(e);
        }
    }

    @Override
    public byte[] generateX509PublicKey(PublicKey publicKey) {
        if (!(publicKey instanceof RSAPublicKey rsaPublicKey)) {
            throw new CryptoException("Invalid type of public key: " + publicKey.getClass());
        }

        BigInteger modulus = new BigInteger(1, rsaPublicKey.getModulus().getByteArrayValue());
        BigInteger publicExponent = new BigInteger(1, rsaPublicKey.getPublicExponent().getByteArrayValue());

        try {
            return keyManagers.getForRSA().generateX509PublicKey(modulus, publicExponent);
        } catch (InvalidKeySpecException e) {
            throw XrdRuntimeException.systemException(e);
        }
    }

    @Override
    protected void setPublicKeyAttributes(RSAPublicKey template, PubKeyAttributes attributes) {
        template.getModulusBits().setLongValue((long) signerProperties.getKeyLength());
        template.getPublicExponent().setByteArrayValue(PUBLIC_EXPONENT_BYTES);
    }

    @Override
    protected RSAPublicKey newPublicKeyTemplate() {
        return new RSAPublicKey();
    }

    @Override
    protected RSAPrivateKey newPrivateKeyTemplate() {
        return new RSAPrivateKey();
    }
}
