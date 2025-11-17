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
package org.niis.xroad.signer.core.tokenmanager.token;

import ee.ria.xroad.common.crypto.KeyManagers;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.signer.core.util.SignerUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

import static ee.ria.xroad.common.util.CryptoUtils.loadPkcs12KeyStore;

/**
 * Utility methods for software token.
 */
@Slf4j
public final class SoftwareTokenUtil {

    private SoftwareTokenUtil() {
    }

    static KeyStore createKeyStore(KeyPair kp, String alias, char[] password, KeyManagers keyManagers)
            throws OperatorCreationException, CertificateException, IOException, NoSuchAlgorithmException, KeyStoreException {
        var signALgo = keyManagers.getFor(kp.getPrivate().getAlgorithm()).getSoftwareTokenKeySignAlgorithm();
        ContentSigner signer = CryptoUtils.createContentSigner(signALgo, kp.getPrivate());

        X509Certificate[] certChain = new X509Certificate[1];
        certChain[0] = SignerUtil.createCertificate("KeyHolder", kp, signer);

        KeyStore keyStore = KeyStore.getInstance("pkcs12");
        keyStore.load(null, null);

        KeyStore.PrivateKeyEntry pkEntry = new KeyStore.PrivateKeyEntry(kp.getPrivate(), certChain);

        keyStore.setEntry(alias, pkEntry, new KeyStore.PasswordProtection(password));

        return keyStore;
    }

    static PrivateKey loadPrivateKey(byte[] keyStoreFile, String alias, char[] password)
            throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
        KeyStore ks = loadPkcs12KeyStore(new ByteArrayInputStream(keyStoreFile), password);
        return loadPrivateKey(ks, alias, password);
    }

    static PrivateKey loadPrivateKey(KeyStore ks, String alias, char[] password)
            throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException {
        PrivateKey privateKey = (PrivateKey) ks.getKey(alias, password);

        if (privateKey == null) {
            // Could not find private key for given alias, attempt to find
            // key for any alias in the key store
            Enumeration<String> aliases = ks.aliases();

            while (aliases.hasMoreElements()) {
                privateKey = (PrivateKey) ks.getKey(aliases.nextElement(), password);

                if (privateKey != null) {
                    return privateKey;
                }
            }

            throw XrdRuntimeException.systemInternalError("Private key not found in keystore '" + ks + "', wrong password?");
        }

        return privateKey;
    }

    public static KeyStore rewriteKeyStoreWithNewPin(KeyStore oldKeyStore, String keyAlias, char[] oldPin, char[] newPin)
            throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
        PrivateKey privateKey = loadPrivateKey(oldKeyStore, keyAlias, oldPin);

        KeyStore newKeyStore = KeyStore.getInstance("pkcs12");
        newKeyStore.load(null, null);
        Certificate[] certChain = oldKeyStore.getCertificateChain(keyAlias);
        KeyStore.PrivateKeyEntry pkEntry = new KeyStore.PrivateKeyEntry(privateKey, certChain);
        newKeyStore.setEntry(keyAlias, pkEntry, new KeyStore.PasswordProtection(newPin));

        return newKeyStore;
    }
}
