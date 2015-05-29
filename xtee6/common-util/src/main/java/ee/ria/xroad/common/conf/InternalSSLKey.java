package ee.ria.xroad.common.conf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import lombok.Data;
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

    private static final String KEY_FILE_NAME = "ssl/internal.p12";
    private static final String KEY_ALIAS = "internal";
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
