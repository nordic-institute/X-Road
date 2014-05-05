package ee.cyber.sdsb.signer.core.token;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.util.PasswordStore;
import ee.cyber.sdsb.signer.core.device.SoftwareTokenType;
import ee.cyber.sdsb.signer.protocol.dto.KeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.TokenInfo;
import ee.cyber.sdsb.signer.protocol.dto.TokenStatusInfo;
import ee.cyber.sdsb.signer.protocol.message.ActivateToken;
import ee.cyber.sdsb.signer.protocol.message.GenerateKey;
import ee.cyber.sdsb.signer.protocol.message.InitSoftwareToken;
import ee.cyber.sdsb.signer.util.SignerUtil;

import static ee.cyber.sdsb.common.ErrorCodes.*;
import static ee.cyber.sdsb.common.util.CryptoUtils.encodeBase64;
import static ee.cyber.sdsb.signer.core.TokenManager.*;
import static ee.cyber.sdsb.signer.core.token.SoftwareTokenUtil.*;

public class SoftwareTokenWorker extends AbstractTokenWorker {

    private static final Logger LOG =
            LoggerFactory.getLogger(SoftwareTokenWorker.class);

    // Use no digesting algorithm, since the input data is already a digest
    private static final String SIGNATURE_ALGORITHM = "NONEwithRSA";

    private final Map<String, PrivateKey> privateKeys = new HashMap<>();

    public SoftwareTokenWorker(TokenInfo tokenInfo, SoftwareTokenType ignored) {
        super(tokenInfo);
    }

    @Override
    protected void onUpdate() throws Exception {
        LOG.trace("onUpdate()");

        updateStatus();

        try {
            findKeysNotInConf();

            if (isPinStored()) {
                updateKeys();
            }
        } catch (Exception e) {
            LOG.error("Failed to add keys not in conf", e);
        }
    }

    @Override
    protected void onMessage(Object message) throws Exception {
        if (message instanceof InitSoftwareToken) {
            initializeToken(((InitSoftwareToken) message).getPin());
            sendSuccessResponse();
        } else {
            super.onMessage(message);
        }
    }

    @Override
    protected void activateToken(ActivateToken message) throws Exception {
        if (message.isActivate()) {
            activateToken();
        }

        setTokenStatus(tokenId, TokenStatusInfo.OK);
    }

    @Override
    protected GenerateKeyResult generateKey(GenerateKey message)
            throws Exception {
        LOG.trace("generateKeys()");
        try {
            java.security.KeyPair keyPair =
                    generateKeyPair(SignerUtil.KEY_SIZE.intValue());

            String keyId = SignerUtil.randomId();
            savePkcs12Keystore(keyPair, keyId, getKeyStoreFileName(keyId),
                    getPin());

            String publicKeyBase64 =
                    encodeBase64(keyPair.getPublic().getEncoded());

            return new GenerateKeyResult(keyId, publicKeyBase64);
        } catch (Exception e) {
            // TODO: Error code
            throw new CodedException(X_FAILED_TO_GENERATE_R_KEY,
                    "Failed to generate key: %s", e);
        }
    }

    @Override
    protected void deleteKey(String keyId) {
        LOG.trace("deleteKey({})", keyId);

        if (!isTokenActive(tokenId)) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Cannot delete key, token '%s' not active", getWorkerId());
        }

        Path path = Paths.get(getKeyStoreFileName(keyId));

        LOG.info("Deleting key file {}", path);
        try {
            Files.deleteIfExists(path);
        } catch (Exception e) {
            LOG.error("Could not delete key file " + path, e);
        }
    }

    @Override
    protected byte[] sign(String keyId, byte[] data) throws Exception {
        LOG.trace("sign({})", keyId);

        if (!isKeyAvailable(keyId)) {
            throw new CodedException(X_KEY_NOT_AVAILABLE,
                    "Key '%s' not available", keyId);
        }

        PrivateKey key = getPrivateKey(keyId);
        if (key == null) {
            throw new CodedException(X_KEY_NOT_FOUND,
                    "Key '%s' not found", keyId);
        }

        LOG.debug("Signing with key '{}'", keyId);
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(key);
            signature.update(data);

            return signature.sign();
        } catch (Exception e) {
            throw new CodedException(X_CANNOT_SIGN,
                    "Signature creation failed: %s", e);
        }
    }

    private void updateStatus() {
        boolean isInitialized = isTokenInitialized();
        setTokenAvailable(tokenId, isInitialized);

        if (!isInitialized) {
            setTokenStatus(tokenId, TokenStatusInfo.NOT_INITIALIZED);
        }

        boolean isActive = isInitialized && isPinStored();
        setTokenActive(tokenId, isActive);

        if (isActive) {
            try {
                activateToken();
            } catch (Exception e) {
                // Swallow exception; the token status reflects the state
                setTokenActive(tokenId, false);
            }
        }
    }

    private void updateKeys() throws Exception {
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
                LOG.trace("Failed to load private key from key store: {}",
                        e.getMessage());
            }
        }
    }

    private void findKeysNotInConf() throws Exception {
        LOG.debug("Searching for new software keys from '{}'", getKeyDir());

        for (String keyId : listKeysOnDisk()) {
            if (!hasKey(keyId)) {
                try {
                    String publicKeyBase64 =
                            isPinStored() ? loadPublicKeyBase64(keyId) : null;

                    LOG.debug("Found new key with id '{}'", keyId);
                    addKey(tokenId, keyId, publicKeyBase64);
                } catch (Exception e) {
                    LOG.error("Failed to read pkcs#12 key '{}': {}", keyId, e);
                }
            }
        }
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
        if (pkey != null) {
            LOG.debug("Found usable key '{}'", keyId);
            privateKeys.put(keyId, pkey);
        }
    }

    private void initializeToken(char[] pin) throws Exception {
        verifyPinProvided(pin);

        LOG.info("Initializing software token with new pin...");

        java.security.KeyPair kp =
                generateKeyPair(SignerUtil.KEY_SIZE.intValue());

        String keyStoreFile = getKeyStoreFileName(PIN_FILE);
        savePkcs12Keystore(kp, PIN_ALIAS, keyStoreFile, pin);

        setTokenAvailable(tokenId, true);
        setTokenStatus(tokenId, TokenStatusInfo.OK);
    }

    private void activateToken() throws Exception {
        try {
            verifyPin(PasswordStore.getPassword(tokenId));

            setTokenStatus(tokenId, TokenStatusInfo.OK);
        } catch (FileNotFoundException e) {
            LOG.error("Software token not initialized", e);

            setTokenStatus(tokenId, TokenStatusInfo.NOT_INITIALIZED);
            throw new CodedException(X_TOKEN_NOT_INITIALIZED,
                    "Token '%s' is not initialized", tokenId);
        } catch (Exception e) {
            LOG.error("Error verifiying token PIN", e);

            setTokenStatus(tokenId, TokenStatusInfo.USER_PIN_INCORRECT);
            throw new CodedException(X_INTERNAL_ERROR, "PIN incorrect");
        }
    }

    private PrivateKey loadPrivateKey(String keyId) throws Exception {
        String keyStoreFile = getKeyStoreFileName(keyId);

        LOG.trace("Loading pkcs#12 private key '{}' from file '{}'", keyId,
                keyStoreFile);

        return SoftwareTokenUtil.loadPrivateKey(keyStoreFile, keyId, getPin());
    }

    private String loadPublicKeyBase64(String keyId) throws Exception {
        String keyStoreFile = getKeyStoreFileName(keyId);

        LOG.trace("Loading pkcs#12 public key '{}' from file '{}'", keyId,
                keyStoreFile);

        java.security.cert.Certificate cert =
                loadCertificate(keyStoreFile, keyId, getPin());
        if (cert == null) {
            LOG.error("No certificate found in '{}' using alias '{}'",
                    keyStoreFile, keyId);
            return null;
        }

        if (cert.getPublicKey() != null) {
            return encodeBase64(cert.getPublicKey().getEncoded());
        }

        return null;
    }

    private static void verifyPin(char[] pin) throws Exception {
        verifyPinProvided(pin);

        // Attempt to load private key from pin key store.
        SoftwareTokenUtil.loadPrivateKey(getKeyStoreFileName(PIN_FILE),
                PIN_ALIAS, pin);
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

    private static void savePkcs12Keystore(KeyPair kp, String alias,
            String keyStoreFile, char[] password) throws Exception {
        KeyStore keyStore = createKeyStore(kp, alias, password);

        LOG.debug("Creating pkcs#12 keystore '{}'", keyStoreFile);

        try (FileOutputStream fos = new FileOutputStream(keyStoreFile)) {
            keyStore.store(fos, password);
        }
    }
}
