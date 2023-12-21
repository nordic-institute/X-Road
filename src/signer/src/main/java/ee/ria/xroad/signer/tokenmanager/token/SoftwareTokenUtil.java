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
package ee.ria.xroad.signer.tokenmanager.token;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.ResourceUtils;
import ee.ria.xroad.signer.util.SignerUtil;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.operator.ContentSigner;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

import static ee.ria.xroad.common.util.CryptoUtils.loadPkcs12KeyStore;
import static java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.GROUP_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;

/**
 * Utility methods for software token.
 */
@Slf4j
public final class SoftwareTokenUtil {

    static final String PIN_ALIAS = "pin";

    static final String PIN_FILE = ".softtoken";

    static final String P12 = ".p12";

    static final FileAttribute<Set<PosixFilePermission>> SOFT_TOKEN_KEY_DIR_PERMISSIONS =
            PosixFilePermissions.asFileAttribute(EnumSet.of(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE, GROUP_READ,
                    GROUP_EXECUTE));
    static final String SOFT_TOKEN_KEY_DIR_NAME = "softtoken";
    static final String SOFT_TOKEN_KEY_BAK_DIR_NAME = ".softtoken.bak";

    // TODO make it configurable.
    private static final String SIGNATURE_ALGORITHM = CryptoUtils.SHA512WITHRSA_ID;

    private static final FilenameFilter P12_FILTER = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
            return name != null && !name.startsWith(PIN_FILE) && name.endsWith(P12);
        }
    };

    private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss");

    private static final String KEY_CONF_FILE = SystemProperties.getKeyConfFile();

    private SoftwareTokenUtil() {
    }

    /**
     * @return true if software token is initialized
     */
    public static boolean isTokenInitialized() {
        return new File(getKeyStoreFileName(PIN_FILE)).exists();
    }

    /**
     * @param keyId the key id
     * @return the key store file name for a key id
     */
    public static String getKeyStoreFileName(String keyId) {
        return getKeyDir() + File.separator + keyId + P12;
    }

    /**
     * @return /path/to/signer/.softtoken.bak
     */
    public static Path getBackupKeyDir() {
        return getKeyDir().toPath().getParent().resolve(SOFT_TOKEN_KEY_BAK_DIR_NAME);
    }

    /**
     * @return key backup dir path with timestamp in the name e.g. /path/to/signer/.softtoken.bak-20210218102059
     */
    public static Path getBackupKeyDirForDateNow() {
        Timestamp nowTimestamp = new Timestamp(System.currentTimeMillis());
        String nowString = TIMESTAMP_FORMAT.format(nowTimestamp);
        return getBackupKeyDir().resolve("-" + nowString);
    }

    /**
     * Create a temp directory for key stores. Used e.g. when changing pin codes for key stores
     * @throws IOException creating temp dir fails
     */
    public static Path createTempKeyDir() throws IOException {
        return Files.createTempDirectory(Paths.get(ResourceUtils.getFullPathFromFileName(KEY_CONF_FILE)),
                SOFT_TOKEN_KEY_DIR_NAME + "-", SOFT_TOKEN_KEY_DIR_PERMISSIONS);
    }

    static List<String> listKeysOnDisk() {
        List<String> keys = new ArrayList<>();

        for (String p12File : getKeyDir().list(P12_FILTER)) {
            keys.add(p12File.substring(0, p12File.indexOf(P12)));
        }

        return keys;
    }

    static File getKeyDir() {
        return new File(ResourceUtils.getFullPathFromFileName(KEY_CONF_FILE)
                + SOFT_TOKEN_KEY_DIR_NAME + File.separator);
    }

    static KeyStore createKeyStore(KeyPair kp, String alias, char[] password) throws Exception {
        ContentSigner signer = CryptoUtils.createContentSigner(SIGNATURE_ALGORITHM, kp.getPrivate());

        X509Certificate[] certChain = new X509Certificate[1];
        certChain[0] = SignerUtil.createCertificate("KeyHolder", kp, signer);

        KeyStore keyStore = KeyStore.getInstance("pkcs12");
        keyStore.load(null, null);

        KeyStore.PrivateKeyEntry pkEntry = new KeyStore.PrivateKeyEntry(kp.getPrivate(), certChain);

        keyStore.setEntry(alias, pkEntry, new KeyStore.PasswordProtection(password));

        return keyStore;
    }

    static PrivateKey loadPrivateKey(String keyStoreFile, String alias, char[] password) throws Exception {
        KeyStore ks = loadPkcs12KeyStore(new File(keyStoreFile), password);
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

            throw new RuntimeException("Private key not found in keystore '" + keyStoreFile + "', wrong password?");
        }

        return privateKey;
    }

    static Certificate loadCertificate(String keyStoreFile, String alias, char[] password) throws Exception {
        KeyStore ks = loadPkcs12KeyStore(new File(keyStoreFile), password);

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
