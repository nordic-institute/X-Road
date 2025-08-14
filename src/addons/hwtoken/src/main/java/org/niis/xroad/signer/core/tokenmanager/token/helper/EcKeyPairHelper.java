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

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.crypto.CryptoException;
import ee.ria.xroad.common.crypto.KeyManagers;
import ee.ria.xroad.common.crypto.NamedCurves;

import iaik.pkcs.pkcs11.Mechanism;
import iaik.pkcs.pkcs11.Session;
import iaik.pkcs.pkcs11.TokenException;
import iaik.pkcs.pkcs11.objects.ECDSAPrivateKey;
import iaik.pkcs.pkcs11.objects.ECDSAPublicKey;
import iaik.pkcs.pkcs11.objects.KeyPair;
import iaik.pkcs.pkcs11.objects.PublicKey;
import iaik.pkcs.pkcs11.wrapper.PKCS11Constants;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;
import org.niis.xroad.signer.core.tokenmanager.module.PrivKeyAttributes;
import org.niis.xroad.signer.core.tokenmanager.module.PubKeyAttributes;
import org.niis.xroad.signer.core.util.SignerUtil;

public final class EcKeyPairHelper extends AbstractKeyPairBuilder<ECDSAPublicKey, ECDSAPrivateKey> implements KeyPairHelper {

    private static final Mechanism KEY_PAIR_GEN_MECHANISM = Mechanism.get(PKCS11Constants.CKM_ECDSA_KEY_PAIR_GEN);

    static final EcKeyPairHelper INSTANCE = new EcKeyPairHelper();

    @Override
    public KeyPair createKeypair(
            Session activeSession,
            String keyLabel,
            PubKeyAttributes pubKeyAttributes,
            PrivKeyAttributes privKeyAttributes) throws TokenException {
        byte[] id = SignerUtil.generateId();
        // XXX maybe use: byte[] id = activeSession.generateRandom(RANDOM_ID_LENGTH);

        return activeSession.generateKeyPair(
                KEY_PAIR_GEN_MECHANISM,
                buildPublicKeyTemplate(id, keyLabel, pubKeyAttributes),
                buildPrivateKeyTemplate(id, keyLabel, privKeyAttributes));
    }

    @Override
    @ArchUnitSuppressed("NoVanillaExceptions") //TODO XRDDEV-2962 review and refactor if needed
    public byte[] generateX509PublicKey(PublicKey publicKey) throws Exception {
        if (!(publicKey instanceof ECDSAPublicKey ecPublicKey)) {
            throw new CryptoException("Invalid type of public key: " + publicKey.getClass());
        }

        return KeyManagers.getForEC().generateX509PublicKey(
                ecPublicKey.getEcdsaParams().getByteArrayValue(),
                ecPublicKey.getEcPoint().getByteArrayValue()
        );
    }

    @Override
    protected void setPublicKeyAttributes(ECDSAPublicKey template, PubKeyAttributes attributes) {
        var curveName = SystemProperties.getSignerKeyNamedCurve();

        template.getEcdsaParams().setByteArrayValue(NamedCurves.getOIDAsBytes(curveName));
    }

    @Override
    protected ECDSAPublicKey newPublicKeyTemplate() {
        return new ECDSAPublicKey();
    }

    @Override
    protected ECDSAPrivateKey newPrivateKeyTemplate() {
        return new ECDSAPrivateKey();
    }
}
