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

package org.niis.xroad.signer.core.tokenmanager.token;

import ee.ria.xroad.common.crypto.KeyManagers;
import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;
import ee.ria.xroad.common.util.CryptoUtils;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.security.KeyStore;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class SoftwareTokenUtilTest {

    private static final String KEY_LABEL = "3A480F9A822DB1AF0349A1184AB84B5B74ED23EF";
    private final KeyManagers keyManagers = new KeyManagers(2048, "secp256r1");

    @Test
    void createKeyStore() throws Exception {
        var keyPair = keyManagers.getFor(KeyAlgorithm.RSA).generateKeyPair();

        KeyStore keyStore = SoftwareTokenUtil.createKeyStore(keyPair, KEY_LABEL, "Secret1234".toCharArray(), keyManagers);

        assertNotNull(keyStore.getKey(KEY_LABEL, "Secret1234".toCharArray()));
    }

    @Test
    void testLoadPrivateKeyFromBytes() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/keystore.p12")) {
            byte[] keystoreBytes = is.readAllBytes();

            assertNotNull(SoftwareTokenUtil.loadPrivateKey(keystoreBytes, KEY_LABEL, "Secret1234".toCharArray()));
        }
    }

    @Test
    void testLoadPrivateKeyFromKeyStore() throws Exception {
        KeyStore keyStore = loadKeyStore();

        assertNotNull(SoftwareTokenUtil.loadPrivateKey(keyStore, KEY_LABEL, "Secret1234".toCharArray()));
    }

    @Test
    void testLoadPrivateKeyAnyAlias() throws Exception {
        KeyStore keyStore = loadKeyStore();

        var key = SoftwareTokenUtil.loadPrivateKey(keyStore, "not-existing-label", "Secret1234".toCharArray());

        assertNotNull(key);
    }

    @Test
    void testRewriteKeyStoreWithNewPin() throws Exception {
        KeyStore keyStore = loadKeyStore();

        KeyStore newKeystore = SoftwareTokenUtil.rewriteKeyStoreWithNewPin(keyStore, KEY_LABEL, "Secret1234".toCharArray(),
                "newPin".toCharArray());

        assertNotNull(newKeystore.getKey(KEY_LABEL, "newPin".toCharArray()));
    }

    private KeyStore loadKeyStore() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/keystore.p12")) {
            return CryptoUtils.loadPkcs12KeyStore(is, "Secret1234".toCharArray());
        }
    }

}
