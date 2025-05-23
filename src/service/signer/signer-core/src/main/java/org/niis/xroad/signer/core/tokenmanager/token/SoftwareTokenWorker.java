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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.crypto.CryptoException;
import ee.ria.xroad.common.crypto.KeyManagers;
import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignMechanism;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.PasswordStore;
import ee.ria.xroad.common.util.TokenPinPolicy;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.core.tokenmanager.TokenManager;
import org.niis.xroad.signer.core.util.SignerUtil;
import org.niis.xroad.signer.proto.ActivateTokenReq;
import org.niis.xroad.signer.proto.GenerateKeyReq;
import org.niis.xroad.signer.protocol.dto.TokenStatusInfo;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertPath;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_TOKEN_PIN_POLICY_FAILURE;
import static ee.ria.xroad.common.ErrorCodes.X_UNSUPPORTED_SIGN_ALGORITHM;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.crypto.identifier.KeyAlgorithm.RSA;
import static ee.ria.xroad.common.crypto.identifier.Providers.BOUNCY_CASTLE;
import static ee.ria.xroad.common.util.CryptoUtils.loadPkcs12KeyStore;
import static ee.ria.xroad.common.util.EncoderUtils.encodeBase64;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static org.niis.xroad.signer.core.tokenmanager.TokenManager.addKey;
import static org.niis.xroad.signer.core.tokenmanager.TokenManager.getKeyInfo;
import static org.niis.xroad.signer.core.tokenmanager.TokenManager.isTokenActive;
import static org.niis.xroad.signer.core.tokenmanager.TokenManager.listKeys;
import static org.niis.xroad.signer.core.tokenmanager.TokenManager.setKeyAvailable;
import static org.niis.xroad.signer.core.tokenmanager.TokenManager.setTokenActive;
import static org.niis.xroad.signer.core.tokenmanager.TokenManager.setTokenAvailable;
import static org.niis.xroad.signer.core.tokenmanager.TokenManager.setTokenStatus;
import static org.niis.xroad.signer.core.tokenmanager.token.SoftwareTokenUtil.P12;
import static org.niis.xroad.signer.core.tokenmanager.token.SoftwareTokenUtil.PIN_ALIAS;
import static org.niis.xroad.signer.core.tokenmanager.token.SoftwareTokenUtil.PIN_FILE;
import static org.niis.xroad.signer.core.tokenmanager.token.SoftwareTokenUtil.createKeyStore;
import static org.niis.xroad.signer.core.tokenmanager.token.SoftwareTokenUtil.createTempKeyDir;
import static org.niis.xroad.signer.core.tokenmanager.token.SoftwareTokenUtil.getBackupKeyDir;
import static org.niis.xroad.signer.core.tokenmanager.token.SoftwareTokenUtil.getBackupKeyDirForDateNow;
import static org.niis.xroad.signer.core.tokenmanager.token.SoftwareTokenUtil.getKeyDir;
import static org.niis.xroad.signer.core.tokenmanager.token.SoftwareTokenUtil.getKeyStoreFileName;
import static org.niis.xroad.signer.core.tokenmanager.token.SoftwareTokenUtil.isTokenInitialized;
import static org.niis.xroad.signer.core.tokenmanager.token.SoftwareTokenUtil.listKeysOnDisk;
import static org.niis.xroad.signer.core.tokenmanager.token.SoftwareTokenUtil.loadCertificate;
import static org.niis.xroad.signer.core.util.ExceptionHelper.keyNotFound;
import static org.niis.xroad.signer.core.util.ExceptionHelper.loginFailed;
import static org.niis.xroad.signer.core.util.ExceptionHelper.pinIncorrect;
import static org.niis.xroad.signer.core.util.ExceptionHelper.tokenNotActive;
import static org.niis.xroad.signer.core.util.ExceptionHelper.tokenNotInitialized;

/**
 * Encapsulates the software token worker which handles software signing and key
 * management.
 */
@Slf4j
public class SoftwareTokenWorker extends AbstractTokenWorker {

    private static final Set<SignAlgorithm> SUPPORTED_ALGORITHMS = Set.of(
            SignAlgorithm.SHA1_WITH_RSA,
            SignAlgorithm.SHA256_WITH_RSA,
            SignAlgorithm.SHA384_WITH_RSA,
            SignAlgorithm.SHA512_WITH_RSA,

            SignAlgorithm.SHA1_WITH_ECDSA,
            SignAlgorithm.SHA256_WITH_ECDSA,
            SignAlgorithm.SHA384_WITH_ECDSA,
            SignAlgorithm.SHA512_WITH_ECDSA
    );
    private static final String UNSUPPORTED_SIGN_ALGORITHM = "unsupported_sign_algorithm";

    private final Map<String, PrivateKey> privateKeys = new HashMap<>();
    private final TokenType tokenType;

    private boolean isTokenLoginAllowed = true;

    /**
     * Creates new worker.
     *
     * @param tokenInfo the token info
     */
    public SoftwareTokenWorker(TokenInfo tokenInfo, TokenType tokenType) {
        super(tokenInfo);
        this.tokenType = tokenType;
    }

    @Override
    public void refresh() {
        log.trace("onUpdate()");

        updateStatus();

        try {
            findKeysNotInConf();

            if (isPinStored()) {
                updateKeys();
            }
        } catch (Exception e) {
            log.error("Failed to add keys not in conf", e);
        }
    }

    @Override
    public void onActionHandled() {
        //No-OP
    }

    @Override
    protected void activateToken(ActivateTokenReq message) {
        if (message.getActivate()) {
            if (!isTokenLoginAllowed) {
                throw loginFailed("PIN change in progress – token login not allowed");
            }
            activateToken();
        } else {
            deactivateToken();
        }

        setTokenStatus(tokenId, TokenStatusInfo.OK);
    }

    @Override
    protected GenerateKeyResult generateKey(GenerateKeyReq message)
            throws Exception {
        log.trace("generateKeys()");

        assertTokenAvailable();

        var keyPair = KeyManagers.getFor(mapAlgorithm(message.getAlgorithm())).generateKeyPair();

        String keyId = SignerUtil.randomId();
        savePkcs12Keystore(keyPair, keyId, getKeyStoreFileName(keyId), getPin());

        String publicKeyBase64 = encodeBase64(keyPair.getPublic().getEncoded());

        return new GenerateKeyResult(keyId, publicKeyBase64);
    }

    @Override
    protected void deleteKey(String keyId) throws Exception {
        log.trace("deleteKey({})", keyId);

        assertTokenAvailable();

        Path path = Paths.get(getKeyStoreFileName(keyId));

        log.info("Deleting key file {}", path);

        Files.deleteIfExists(path);
    }

    @Override
    protected void deleteCert(String certId) {
        TokenManager.removeCert(certId);
    }

    @Override
    protected byte[] sign(String keyId, SignAlgorithm signatureAlgorithmId, byte[] data) throws Exception {
        log.trace("sign({}, {})", keyId, signatureAlgorithmId);

        assertTokenAvailable();

        assertKeyAvailable(keyId);

        PrivateKey key = getPrivateKey(keyId);

        if (key == null) {
            throw keyNotFound(keyId);
        }
        var keyAlgorithm = KeyAlgorithm.valueOf(key.getAlgorithm());
        checkSignatureAlgorithm(signatureAlgorithmId, keyAlgorithm);

        if (!keyAlgorithm.equals(signatureAlgorithmId.algorithm())) {
            throw CodedException.tr(X_UNSUPPORTED_SIGN_ALGORITHM, UNSUPPORTED_SIGN_ALGORITHM,
                    "Unsupported signature algorithm '%s' for key algorithm '%s'", signatureAlgorithmId.name(), keyAlgorithm);
        }

        log.debug("Signing with key '{}' and signature algorithm '{}'", keyId, signatureAlgorithmId);


        SignAlgorithm signAlgorithm = KeyManagers.getFor(keyAlgorithm).getSoftwareTokenSignAlgorithm();
        Signature signature = Signature.getInstance(signAlgorithm.name(), BOUNCY_CASTLE);
        signature.initSign(key);
        signature.update(data);

        return signature.sign();
    }

    private static void checkSignatureAlgorithm(SignAlgorithm signatureAlgorithmId, KeyAlgorithm algorithm) throws CodedException {
        if (!SUPPORTED_ALGORITHMS.contains(signatureAlgorithmId)) {
            throw CodedException.tr(X_UNSUPPORTED_SIGN_ALGORITHM, UNSUPPORTED_SIGN_ALGORITHM,
                    "Unsupported signature algorithm '%s'", signatureAlgorithmId.name());
        }

        if (!algorithm.equals(signatureAlgorithmId.algorithm())) {
            throw CodedException.tr(X_UNSUPPORTED_SIGN_ALGORITHM, UNSUPPORTED_SIGN_ALGORITHM,
                    "Unsupported signature algorithm '%s' for key algorithm '%s'", signatureAlgorithmId.name(), algorithm);
        }
    }


    protected byte[] signCertificate(String keyId, SignAlgorithm signatureAlgorithmId, String subjectName, PublicKey publicKey)
            throws Exception {
        log.trace("signCertificate({}, {}, {})", keyId, signatureAlgorithmId, subjectName);
        assertTokenAvailable();
        assertKeyAvailable(keyId);
        KeyInfo keyInfo = getKeyInfo(keyId);
        PrivateKey privateKey = getPrivateKey(keyId);

        var keyAlgorithm = KeyAlgorithm.valueOf(privateKey.getAlgorithm());
        checkSignatureAlgorithm(signatureAlgorithmId, keyAlgorithm);

        CertificateInfo certificateInfo = keyInfo.getCerts().getFirst();
        X509Certificate issuerX509Certificate = CryptoUtils.readCertificate(certificateInfo.getCertificateBytes());
        JcaX509v3CertificateBuilder certificateBuilder = getCertificateBuilder(subjectName, publicKey,
                issuerX509Certificate);

        log.debug("Signing certificate with key '{}' and signature algorithm '{}'", keyId, signatureAlgorithmId);
        ContentSigner signer = new JcaContentSignerBuilder(signatureAlgorithmId.name()).build(privateKey);
        X509CertificateHolder certHolder = certificateBuilder.build(signer);
        X509Certificate signedCert = new JcaX509CertificateConverter().getCertificate(certHolder);
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509", BOUNCY_CASTLE);
        CertPath certPath = certificateFactory.generateCertPath(Arrays.asList(signedCert, issuerX509Certificate));
        return certPath.getEncoded("PEM");
    }

    // ------------------------------------------------------------------------

    private void updateStatus() {
        boolean isInitialized = isTokenInitialized();

        if (!isInitialized) {
            setTokenStatus(tokenId, TokenStatusInfo.NOT_INITIALIZED);
        }

        boolean isActive = isInitialized && isPinStored();
        setTokenActive(tokenId, isActive);

        if (isActive) {
            try {
                activateToken();
            } catch (Exception e) {
                setTokenActive(tokenId, false);

                log.trace("Failed to activate token", e);
            }
        }
    }

    private void updateKeys() {
        for (KeyInfo keyInfo : listKeys(tokenId)) {
            String keyId = keyInfo.getId();

            setKeyAvailable(keyId, true);

            if (privateKeys.containsKey(keyId)) {
                continue;
            }

            try {
                initializePrivateKey(keyId);
            } catch (Exception e) {
                setKeyAvailable(keyId, false);

                log.trace("Failed to load private key from key store", e);
            }
        }
    }

    private void findKeysNotInConf() {
        log.debug("Searching for new software keys from '{}'", getKeyDir());

        for (String keyId : listKeysOnDisk()) {
            if (isKeyMissing(keyId)) {
                try {
                    Optional<KeyData> publicKey = isPinStored() ? loadPublicKeyBase64(keyId) : Optional.empty();

                    log.debug("Found new key with id '{}'", keyId);

                    var signMechanism = resolveSignMechanism(publicKey.map(KeyData::algorithm).orElse(null));
                    addKey(tokenId, keyId, publicKey.map(KeyData::data).orElse(null), signMechanism);
                } catch (Exception e) {
                    log.error("Failed to read pkcs#12 key '{}'", keyId, e);
                }
            }
        }
    }

    protected SignMechanism resolveSignMechanism(KeyAlgorithm algorithm) {
        return tokenType.resolveSignMechanismName(algorithm)
                .orElseThrow(() -> new CryptoException("Unsupported key algorithm: " + algorithm));
    }

    private PrivateKey getPrivateKey(String keyId) throws Exception {
        PrivateKey pkey = privateKeys.get(keyId);

        if (pkey == null) {
            initializePrivateKey(keyId);
        }

        return privateKeys.get(keyId);
    }

    private void initializePrivateKey(String keyId) throws Exception {
        PrivateKey pkey = loadPrivateKey(keyId);

        log.debug("Found usable key '{}'", keyId);

        privateKeys.put(keyId, pkey);
    }

    @Override
    public void initializeToken(char[] pin) {
        verifyPinProvided(pin);

        log.info("Initializing software token with new pin...");

        if (SystemProperties.shouldEnforceTokenPinPolicy() && !TokenPinPolicy.validate(pin)) {
            throw new CodedException(X_TOKEN_PIN_POLICY_FAILURE, "Token PIN does not meet complexity requirements");
        }

        try {
            java.security.KeyPair kp = KeyManagers.getFor(SystemProperties.getSofTokenPinKeystoreAlgorithm()).generateKeyPair();

            String keyStoreFile = getKeyStoreFileName(PIN_FILE);
            savePkcs12Keystore(kp, PIN_ALIAS, keyStoreFile, pin);

            setTokenAvailable(tokenId, true);
            setTokenStatus(tokenId, TokenStatusInfo.OK);
        } catch (Exception e) {
            throw translateException(e);
        }
    }

    private void rewriteKeyStoreWithNewPin(String keyFile, String keyAlias, char[] oldPin, char[] newPin,
                                           Path tempKeyDir) throws Exception {
        String keyStoreFile = getKeyStoreFileName(keyFile);
        Path tempKeyStoreFile = tempKeyDir.resolve(keyFile + P12);

        KeyStore oldKeyStore = loadPkcs12KeyStore(new File(keyStoreFile), oldPin);
        PrivateKey privateKey = SoftwareTokenUtil.loadPrivateKey(keyStoreFile, keyAlias, oldPin);

        KeyStore newKeyStore = KeyStore.getInstance("pkcs12");
        newKeyStore.load(null, null);
        Certificate[] certChain = oldKeyStore.getCertificateChain(keyAlias);
        KeyStore.PrivateKeyEntry pkEntry = new KeyStore.PrivateKeyEntry(privateKey, certChain);
        newKeyStore.setEntry(keyAlias, pkEntry, new KeyStore.PasswordProtection(newPin));

        try (OutputStream os = Files.newOutputStream(tempKeyStoreFile, StandardOpenOption.CREATE,
                StandardOpenOption.DSYNC)) {
            newKeyStore.store(os, newPin);
        }
    }

    private void verifyOldAndNewPin(char[] oldPin, char[] newPin) throws Exception {
        // Verify that pin is provided and get the correct pin
        char[] oldPinFromStore = getPin();
        try {
            verifyPin(oldPin);
        } catch (Exception e) {
            log.error("Error verifying token PIN", e);
            setTokenStatus(tokenId, TokenStatusInfo.USER_PIN_INCORRECT);
            throw pinIncorrect();
        }
        if (!Arrays.equals(oldPinFromStore, oldPin)) {
            log.error("The PIN provided for updating the pin was incorrect");
            setTokenStatus(tokenId, TokenStatusInfo.USER_PIN_INCORRECT);
            throw pinIncorrect();
        }
        // Verify new pin complexity
        if (SystemProperties.shouldEnforceTokenPinPolicy() && !TokenPinPolicy.validate(newPin)) {
            throw new CodedException(X_TOKEN_PIN_POLICY_FAILURE,
                    "Token PIN does not meet complexity requirements");
        }
    }

    /**
     * Rename the key folder (softtoken) to .softtoken.bak
     * Extremely rare corner case: if that folder already exists, keep the existing folder but rename it with a
     * timestamp e.g. .softtoken.bak-20210218155510. This way the pin change gets to proceed and possibly important
     * backup folder does not get removed.
     *
     * @throws IOException problems with files
     */
    private void createKeyDirBackup() throws IOException {
        File backupDir = getBackupKeyDir().toFile();
        // If an old .softtoken.bak folder exists, rename the folder with a timestamp
        if (backupDir.exists()) {
            File timestampedBackupDir = getBackupKeyDirForDateNow().toFile();
            log.warn("A backup folder already exists. Renaming the folder to {}", timestampedBackupDir.getName());
            FileUtils.moveDirectory(backupDir, timestampedBackupDir);
        }
        // Change the key dir name to .softtoken.bak
        Files.move(getKeyDir().toPath(), getBackupKeyDir(), ATOMIC_MOVE);
    }

    @Override
    public void handleUpdateTokenPin(char[] oldPin, char[] newPin) {
        log.info("Updating the software token pin to a new one...");

        isTokenLoginAllowed = false; // Prevent token login for the time of pin update
        try {
            verifyOldAndNewPin(oldPin, newPin);
            // Clear pin from pwstore and deactivate token
            PasswordStore.storePassword(tokenId, null);
            deactivateToken();
            // Create a new temp folder for working
            Path tempKeyDir = createTempKeyDir();
            // Rewrite the ".softtoken" keystore with a new pin in the temp folder
            rewriteKeyStoreWithNewPin(PIN_FILE, PIN_ALIAS, oldPin, newPin, tempKeyDir);
            // Rewrite all other keystores with the new pin
            for (String keyId : listKeysOnDisk()) {
                rewriteKeyStoreWithNewPin(keyId, keyId, oldPin, newPin, tempKeyDir);
            }
            createKeyDirBackup();
            // Change the temp dir name to 'softtoken'
            Files.move(tempKeyDir, getKeyDir().toPath(), ATOMIC_MOVE);
            // All good: remove the backup folder
            FileUtils.deleteDirectory(getBackupKeyDir().toFile());
            log.info("Updating the software token pin was successful!");
        } catch (Exception e) {
            log.info("Updating the software token pin failed!");
            throw translateException(e);
        } finally {
            isTokenLoginAllowed = true; // Allow token login again
        }
    }

    private void activateToken() {
        try {
            verifyPin(PasswordStore.getPassword(tokenId));

            setTokenStatus(tokenId, TokenStatusInfo.OK);
            setTokenActive(tokenId, true);
        } catch (FileNotFoundException e) {
            log.error("Software token not initialized", e);

            setTokenStatus(tokenId, TokenStatusInfo.NOT_INITIALIZED);

            throw tokenNotInitialized(tokenId);
        } catch (Exception e) {
            log.error("Error verifying token PIN", e);

            setTokenStatus(tokenId, TokenStatusInfo.USER_PIN_INCORRECT);

            throw pinIncorrect();
        }
    }

    private void deactivateToken() {
        privateKeys.clear();

        setTokenActive(tokenId, false);
    }

    private PrivateKey loadPrivateKey(String keyId) throws Exception {
        String keyStoreFile = getKeyStoreFileName(keyId);

        log.trace("Loading pkcs#12 private key '{}' from file '{}'", keyId, keyStoreFile);

        return SoftwareTokenUtil.loadPrivateKey(keyStoreFile, keyId, getPin());
    }

    private Optional<KeyData> loadPublicKeyBase64(String keyId) throws Exception {
        String keyStoreFile = getKeyStoreFileName(keyId);

        log.trace("Loading pkcs#12 public key '{}' from file '{}'", keyId, keyStoreFile);

        java.security.cert.Certificate cert = loadCertificate(keyStoreFile, keyId, getPin());

        if (cert == null) {
            log.error("No certificate found in '{}' using alias '{}'", keyStoreFile, keyId);

            return Optional.empty();
        }

        if (cert.getPublicKey() != null) {
            var algorithm = cert.getPublicKey().getAlgorithm() == null ? RSA : KeyAlgorithm.valueOf(cert.getPublicKey().getAlgorithm());
            return Optional.of(new KeyData(encodeBase64(cert.getPublicKey().getEncoded()), algorithm));
        }

        return Optional.empty();
    }

    private static void verifyPin(char[] pin) throws Exception {
        verifyPinProvided(pin);

        // Attempt to load private key from pin key store.
        SoftwareTokenUtil.loadPrivateKey(getKeyStoreFileName(PIN_FILE), PIN_ALIAS, pin);
    }

    private char[] getPin() throws Exception {
        final char[] pin = PasswordStore.getPassword(tokenId);
        verifyPinProvided(pin);

        return pin;
    }

    private static void verifyPinProvided(char[] pin) {
        if (pin == null || pin.length == 0) {
            throw new CodedException(X_INTERNAL_ERROR, "PIN not provided");
        }
    }

    private static void savePkcs12Keystore(KeyPair kp, String alias, String keyStoreFile, char[] password)
            throws Exception {
        KeyStore keyStore = createKeyStore(kp, alias, password);

        log.debug("Creating pkcs#12 keystore '{}'", keyStoreFile);

        try (FileOutputStream fos = new FileOutputStream(keyStoreFile)) {
            keyStore.store(fos, password);
        }
    }

    private void assertTokenAvailable() {
        if (!isTokenActive(tokenId)) {
            throw tokenNotActive(tokenId);
        }
    }

    @Override
    public boolean isSoftwareToken() {
        return true;
    }

    private record KeyData(String data, KeyAlgorithm algorithm) {
    }
}
