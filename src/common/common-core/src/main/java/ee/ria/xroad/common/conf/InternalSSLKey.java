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
package ee.ria.xroad.common.conf;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;

import lombok.Getter;
import lombok.Value;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.util.CryptoUtils.loadPkcs12KeyStore;

/**
 * The certificate and private key for internal TLS communications are held
 * in a pkcs11 file.
 */
@Value
public final class InternalSSLKey {

    public static final String PK_FILE_NAME = "ssl/internal.key";
    public static final String CRT_FILE_NAME = "ssl/internal.crt";
    public static final String KEY_FILE_NAME = "ssl/internal.p12";
    public static final String KEY_ALIAS = "internal";
    @Getter
    private static final char[] KEY_PASSWORD = KEY_ALIAS.toCharArray();

    private final PrivateKey key;
    private final X509Certificate[] certChain;

    /**
     * Loads the SSL key (the 'internal' key by default) from the pkcs11 file.
     *
     * @return the internal ssl key
     * @throws Exception if an error occurs while loading
     */
    public static InternalSSLKey load() throws Exception {
        return load(KEY_FILE_NAME, KEY_ALIAS, KEY_PASSWORD);
    }

    /**
     * Loads the SSL key with the given name from the pkcs11 file.
     *
     * @param keyName the name of the key to load
     * @return the internal ssl key
     * @throws Exception if an error occurs while loading
     */
    public static InternalSSLKey load(String keyName) throws Exception {
        String filename = String.format("ssl/%s.p12", keyName);
        return load(filename, keyName, keyName.toCharArray());
    }

    private static InternalSSLKey load(String filename, String keyAlias, char[] keyPassword) throws Exception {
        Path file = Paths.get(SystemProperties.getConfPath(), filename);
        if (Files.exists(file)) {
            KeyStore ks = loadPkcs12KeyStore(file.toFile(), keyPassword);

            PrivateKey key = (PrivateKey) ks.getKey(keyAlias, keyPassword);
            if (key == null) {
                throw new CodedException(X_INTERNAL_ERROR,
                        "Could not get key from '%s'", file);
            }

            Certificate[] chain = ks.getCertificateChain(keyAlias);
            if (chain == null || chain.length < 1 || !(chain[0] instanceof X509Certificate)) {
                throw new CodedException(X_INTERNAL_ERROR, "Could not get certificate from '%s'", file);
            }
            X509Certificate[] tmp = new X509Certificate[chain.length];
            System.arraycopy(chain, 0, tmp, 0, chain.length);

            return new InternalSSLKey(key, tmp);
        }

        return null;
    }
}
