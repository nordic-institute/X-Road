package ee.cyber.sdsb.signer.core.token;

import java.io.File;
import java.io.FilenameFilter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import org.bouncycastle.operator.ContentSigner;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.util.ResourceUtils;
import ee.cyber.sdsb.signer.util.SignerUtil;

import static ee.cyber.sdsb.common.util.CryptoUtils.createDefaultContentSigner;
import static ee.cyber.sdsb.common.util.CryptoUtils.loadKeyStore;

public final class SoftwareTokenUtil {

    static final String PIN_ALIAS = "pin";

    static final String PIN_FILE = ".softtoken";

    static final String P12 = ".p12";

    private static final FilenameFilter P12_FILTER = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
            return name != null && !name.startsWith(PIN_FILE)
                    && name.endsWith(P12);
        }
    };

    public static boolean isTokenInitialized() {
        return new File(getKeyStoreFileName(PIN_FILE)).exists();
    }

    public static String getKeyStoreFileName(String keyId) {
        return getKeyDir() + "/" + keyId + P12;
    }

    static List<String> listKeysOnDisk() {
        List<String> keys = new ArrayList<>();
        for (String p12File : getKeyDir().list(P12_FILTER)) {
            keys.add(p12File.substring(0, p12File.indexOf(P12)));
        }

        return keys;
    }

    static File getKeyDir() {
        return new File(ResourceUtils.getFullPathFromFileName(
                SystemProperties.getKeyConfFile()));
    }

    static KeyStore createKeyStore(KeyPair kp, String alias, char[] password)
            throws Exception {
        ContentSigner signer = createDefaultContentSigner(kp.getPrivate());

        X509Certificate[] certChain = new X509Certificate[1];
        certChain[0] = SignerUtil.createCertificate("KeyHolder", kp, signer);

        KeyStore keyStore = KeyStore.getInstance("pkcs12");
        keyStore.load(null, null);

        KeyStore.PrivateKeyEntry pkEntry =
                new KeyStore.PrivateKeyEntry(kp.getPrivate(), certChain);

        keyStore.setEntry(alias, pkEntry,
                new KeyStore.PasswordProtection(password));

        return keyStore;
    }

    static PrivateKey loadPrivateKey(String keyStoreFile, String alias,
            char[] password) throws Exception {
        KeyStore ks = loadKeyStore("pkcs12", keyStoreFile, password);

        PrivateKey privateKey = (PrivateKey) ks.getKey(alias, password);
        if (privateKey == null) {
            // Could not find private key for given alias, attempt to find
            // key for any alias in the key store
            Enumeration<String> aliases = ks.aliases();
            while (aliases.hasMoreElements()) {
                privateKey = (PrivateKey) ks.getKey(aliases.nextElement(),
                        password);
                if (privateKey != null) {
                    return privateKey;
                }
            }

            throw new RuntimeException("Private key not found in keystore '"
                    + keyStoreFile + "', wrong password?");
        }

        return privateKey;
    }

    static Certificate loadCertificate(String keyStoreFile, String alias,
            char[] password) throws Exception {
        KeyStore ks = loadKeyStore("pkcs12", keyStoreFile, password);

        Certificate cert = ks.getCertificate(alias);
        if (cert == null) {
            // Could not find certificate for given alias, attempt to find
            // certificate for any alias in the key store
            Enumeration<String> aliases = ks.aliases();
            while (aliases.hasMoreElements()) {
                cert = ks.getCertificate(aliases.nextElement());
                if (cert != null) {
                    return cert;
                }
            }
        }

        return cert;
    }

    static KeyPair generateKeyPair(int keySize) throws Exception {
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
        keyPairGen.initialize(keySize, new SecureRandom());

        return keyPairGen.generateKeyPair();
    }
}
