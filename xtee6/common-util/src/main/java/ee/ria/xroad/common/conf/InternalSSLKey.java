/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.util.CryptoUtils.loadPkcs12KeyStore;

/**
 * The certificate and private key for internal TLS communications are held
 * in a pkcs11 file.
 */
@Data
@RequiredArgsConstructor
public final class InternalSSLKey {

    public static final String PK_FILE_NAME = "ssl/internal.key";
    public static final String CRT_FILE_NAME = "ssl/internal.crt";
    public static final String KEY_FILE_NAME = "ssl/internal.p12";
    public static final String KEY_ALIAS = "internal";
    @Getter
    private static final char[] KEY_PASSWORD = "internal".toCharArray();

    private final PrivateKey key;
    private final X509Certificate cert;

    private InternalSSLKey() {
        key = null;
        cert = null;
    }

    /**
     * Loads the SSL key from the pkcs11 file.
     * @return the internal ssl key
     * @throws Exception if an error occurs while loading
     */
    public static InternalSSLKey load() throws Exception {
        Path file = Paths.get(SystemProperties.getConfPath(), KEY_FILE_NAME);
        if (Files.exists(file)) {
            KeyStore ks = loadPkcs12KeyStore(file.toFile(), KEY_PASSWORD);

            PrivateKey key = (PrivateKey) ks.getKey(KEY_ALIAS, KEY_PASSWORD);
            if (key == null) {
                throw new CodedException(X_INTERNAL_ERROR,
                        "Could not get key from '%s'", file);
            }

            X509Certificate cert =
                    (X509Certificate) ks.getCertificate(KEY_ALIAS);
            if (cert == null) {
                throw new CodedException(X_INTERNAL_ERROR,
                        "Could not get certificate from '%s'", file);
            }

            return new InternalSSLKey(key, cert);
        }

        return null;
    }
}
