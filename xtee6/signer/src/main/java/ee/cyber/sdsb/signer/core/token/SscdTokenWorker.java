package ee.cyber.sdsb.signer.core.token;

import iaik.pkcs.pkcs11.Mechanism;
import iaik.pkcs.pkcs11.Session;
import iaik.pkcs.pkcs11.Token;
import iaik.pkcs.pkcs11.objects.KeyPair;
import iaik.pkcs.pkcs11.objects.RSAPrivateKey;
import iaik.pkcs.pkcs11.objects.RSAPublicKey;
import iaik.pkcs.pkcs11.objects.X509PublicKeyCertificate;
import iaik.pkcs.pkcs11.wrapper.PKCS11Constants;
import iaik.pkcs.pkcs11.wrapper.PKCS11Exception;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.DatatypeConverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.util.PasswordStore;
import ee.cyber.sdsb.signer.core.device.SscdTokenType;
import ee.cyber.sdsb.signer.protocol.dto.CertificateInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.TokenInfo;
import ee.cyber.sdsb.signer.protocol.dto.TokenStatusInfo;
import ee.cyber.sdsb.signer.protocol.message.ActivateToken;
import ee.cyber.sdsb.signer.protocol.message.GenerateKey;
import ee.cyber.sdsb.signer.util.SignerUtil;

import static ee.cyber.sdsb.common.ErrorCodes.*;
import static ee.cyber.sdsb.common.util.CryptoUtils.encodeBase64;
import static ee.cyber.sdsb.signer.core.TokenManager.*;
import static ee.cyber.sdsb.signer.core.token.SscdTokenUtil.*;
import static ee.cyber.sdsb.signer.util.SignerUtil.keyId;
import static iaik.pkcs.pkcs11.Token.SessionType.SERIAL_SESSION;

public class SscdTokenWorker extends AbstractTokenWorker {

    private static final Logger LOG =
            LoggerFactory.getLogger(SscdTokenWorker.class);

    private static final Mechanism SIGN_MECHANISM =
            Mechanism.get(PKCS11Constants.CKM_RSA_PKCS);

    private static final Mechanism KEYGEN_MECHANISM =
            Mechanism.get(PKCS11Constants.CKM_RSA_PKCS_KEY_PAIR_GEN);

    private final SscdTokenType tokenType;

    // maps key id (hex) to RSAPrivateKey
    private final Map<String, RSAPrivateKey> privateKeys = new HashMap<>();
    private final Map<String, RSAPublicKey> publicKeys = new HashMap<>();
    private final Map<String, X509PublicKeyCertificate> certs = new HashMap<>();

    private Session activeSession;

    public SscdTokenWorker(TokenInfo tokenInfo, SscdTokenType tokenType) {
        super(tokenInfo);

        this.tokenType = tokenType;
    }

    @Override
    public void postStop() throws Exception {
        closeActiveSession();
    }

    @Override
    protected void onUpdate() throws Exception {
        LOG.trace("onUpdate()");

        if (isTokenAvailable(tokenId) && activeSession != null) {
            findKeysNotInConf();
            findPublicKeysForPrivateKeys();
            findCertificatesNotInConf();
        } else { // needs init
            doInitSequence();
        }

        setTokenActive(tokenId, isPinStored());
    }

    // ----------------------- Message handlers -------------------------------

    @Override
    protected void activateToken(ActivateToken message) throws Exception {
        if (message.isActivate()) { // login
            LOG.info("Logging in token '{}'", message.getTokenId());
            try {
                login();
            } catch (Exception e) {
                throw new CodedException(X_INTERNAL_ERROR,
                        "Login failed: %s", e.getMessage());
            }
        } else { // logout
            LOG.info("Logging out token '{}'", message.getTokenId());
            try {
                logout();
            } catch (Exception e) {
                throw new CodedException(X_INTERNAL_ERROR,
                        "Logout failed: %s", e.getMessage());
            }
        }
    }

    @Override
    protected GenerateKeyResult generateKey(GenerateKey message)
            throws Exception {
        LOG.trace("generateKeys()");

        if (tokenType.isReadOnly()) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Cannot generate keys, token is read only");
        }

        if (activeSession == null) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Cannot generate keys, no session");
        }

        byte[] id = SignerUtil.generateId();
        try {
            RSAPublicKey rsaPublicKeyTemplate = new RSAPublicKey();
            rsaPublicKeyTemplate.getId().setByteArrayValue(id);
            setPublicKeyAttributes(rsaPublicKeyTemplate);

            RSAPrivateKey rsaPrivateKeyTemplate = new RSAPrivateKey();
            rsaPrivateKeyTemplate.getId().setByteArrayValue(id);
            setPrivateKeyAttributes(rsaPrivateKeyTemplate);

            // TODO: isPinVerificationPerSigning?

            KeyPair generatedKP = activeSession.generateKeyPair(
                    KEYGEN_MECHANISM, rsaPublicKeyTemplate,
                        rsaPrivateKeyTemplate);

            RSAPrivateKey privateKey =
                    (RSAPrivateKey) generatedKP.getPrivateKey();
            if (privateKey == null) {
                throw new CodedException(X_FAILED_TO_GENERATE_R_KEY);
            }

            RSAPublicKey publicKey = (RSAPublicKey) generatedKP.getPublicKey();
            if (publicKey == null) {
                throw new CodedException(X_FAILED_TO_GENERATE_U_KEY);
            }

            byte[] keyIdBytes = privateKey.getId().getByteArrayValue();
            String keyId = DatatypeConverter.printHexBinary(keyIdBytes);

            byte[] publicKeyBytes = generateX509PublicKey(publicKey);
            String publicKeyBase64 = encodeBase64(publicKeyBytes);

            return new GenerateKeyResult(keyId, publicKeyBase64);
        } catch (Exception e) {
            // TODO: Error code
            throw new CodedException(X_FAILED_TO_GENERATE_R_KEY,
                    "Failed to generate key: %s", e);
        }
    }

    @Override
    protected void deleteKey(String keyId) throws Exception {
        LOG.trace("deleteKey({})", keyId);

        if (tokenType.isReadOnly()) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Cannot delete keys, token is read only");
        }

        if (activeSession == null) {
            throw new CodedException(X_INTERNAL_ERROR,  "No session");
        }

        RSAPrivateKey privateKey = privateKeys.get(keyId);
        if (privateKey != null) {
            LOG.info("Deleting private key '{}' on token '{}'", keyId,
                    getWorkerId());
            try {
                activeSession.destroyObject(privateKey);
                privateKeys.remove(keyId);
            } catch (Exception e) {
                throw new CodedException(X_INTERNAL_ERROR,
                        "Failed to delete private key '%s' on token '%s': %s",
                        new Object[] { keyId, getWorkerId(), e });
            }
        } else {
            LOG.warn("Could not find private key '{}' on token '{}'", keyId,
                    getWorkerId());
        }

        //RSAPublicKey publicKey = publicKeys.get(keyId);
        RSAPublicKey publicKey = findPublicKey(activeSession, keyId);
        if (publicKey != null) {
            LOG.info("Deleting public key '{}' on token '{}'", keyId,
                    getWorkerId());
            try {
                activeSession.destroyObject(publicKey);
                publicKeys.remove(keyId);
            } catch (Exception e) {
                throw new CodedException(X_INTERNAL_ERROR,
                        "Failed to delete public key '%s' on token '%s': %s",
                        new Object[] { keyId, getWorkerId(), e });
            }
        } else {
            LOG.warn("Could not find public key '{}' on token '{}'", keyId,
                    getWorkerId());
        }

        X509PublicKeyCertificate cert = certs.get(keyId);
        if (cert != null) {
            LOG.info("Deleting public key '{}' on token '{}'", keyId,
                    getWorkerId());
            try {
                activeSession.destroyObject(cert);
                certs.remove(keyId);
            } catch (Exception e) {
                throw new CodedException(X_INTERNAL_ERROR,
                        "Failed to delete certificate for key '%s' " +
                        "on token '%s': %s",
                            new Object[] { keyId, getWorkerId(), e });
            }
        }
    }

    @Override
    protected byte[] sign(String keyId, byte[] data) throws Exception {
        LOG.trace("sign({})", keyId);

        if (activeSession == null) {
            throw new CodedException(X_CANNOT_SIGN,  "No session");
        }

        if (tokenType.isPinVerificationPerSigning()) {
            try {
                login();
            } catch (Exception e) {
                throw new CodedException(X_CANNOT_SIGN, "Login failed: %s", e);
            }
        }

        if (!isKeyAvailable(keyId)) {
            throw new CodedException(X_KEY_NOT_AVAILABLE,
                    "Key '%s' not available", keyId);
        }

        RSAPrivateKey key = privateKeys.get(keyId);
        if (key == null) {
            throw new CodedException(X_KEY_NOT_FOUND,
                    "Key '%s' not found on token", keyId);
        }

        LOG.debug("Signing with key '{}'", keyId);
        try {
            activeSession.signInit(SIGN_MECHANISM, key);
            byte[] signature = activeSession.sign(data);
            return signature;
        } catch (Exception e) {
            throw new CodedException(X_CANNOT_SIGN,
                    "Signature creation failed: %s", e);
        } finally {
            if (tokenType.isPinVerificationPerSigning()) {
                try {
                    logout();
                } catch (Exception e) {
                    LOG.error("Logout failed", e);
                }
            }
        }
    }

    // ------------------------------------------------------------------------

    private void findKeysNotInConf() {
        try {
            List<RSAPublicKey> keysOnToken = findPublicKeys(activeSession);
            LOG.debug("Found {} keys on token '{}'", keysOnToken.size(),
                    getWorkerId());

            for (RSAPublicKey keyOnDevice : keysOnToken) {
                String keyId = keyId(keyOnDevice);
                KeyInfo key = getKeyInfo(keyId);
                if (key == null) {
                    key = addKey(tokenId, keyId, null);

                    LOG.debug("Found new key with id '{}' on token '{}'",
                            keyId, getWorkerId());
                }

                if (key.getPublicKey() == null) {
                    updatePublicKey(keyId);
                }

                setKeyAvailable(keyId, privateKeys.containsKey(keyId));
            }
        } catch (Exception e) {
            LOG.error("Failed to find keys from token '{}': {}",
                    getWorkerId(), e);
        }
    }

    private void findPublicKeysForPrivateKeys() throws Exception {
        for (KeyInfo key : listKeys(tokenId)) {
            if (key.getPublicKey() == null) {
                updatePublicKey(key.getId());
            }
        }
    }

    private void findCertificatesNotInConf() {
        try {
            List<X509PublicKeyCertificate> certsOnDevice =
                    findPublicKeyCertificates(activeSession);

            LOG.debug("Found {} certificates on token '{}'",
                    certsOnDevice.size(), getWorkerId());

            List<KeyInfo> existingKeys = listKeys(tokenId);
            for (X509PublicKeyCertificate certOnDevice : certsOnDevice) {
                byte[] certBytes = certOnDevice.getValue().getByteArrayValue();
                for (KeyInfo key : existingKeys) {
                    if (key.getId().equals(keyId(certOnDevice))
                            && !hasCert(key, certBytes)) {
                        LOG.debug("Found new certificate for key '{}'",
                                key.getId());

                        addCert(key.getId(), certBytes);
                        certs.put(key.getId(), certOnDevice);
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Failed to find certificates not in conf", e);
        }
    }

    private void updatePublicKey(String keyId) {
        LOG.debug("Finding public key for key '{}'...", keyId);
        try {
            RSAPublicKey publicKey = findPublicKey(activeSession, keyId);
            if (publicKey != null) {
                String publicKeyBase64 =
                        encodeBase64(generateX509PublicKey(publicKey));
                setPublicKey(keyId, publicKeyBase64);
                publicKeys.put(keyId, publicKey);
            }
        } catch (Exception e) {
            LOG.error("Failed to find public key for key " + keyId, e);
        }
    }

    // ------------------------------------------------------------------------

    private void doInitSequence() {
        LOG.trace("doInit()");
        try {
            createSession();
            updateTokenInfo();

            login();

            setTokenAvailable(tokenId, true);
        } catch (Exception e) {
            LOG.error("Error initializing token ({}): {}", getWorkerId(), e);

            setTokenAvailable(tokenId, false);
        }
    }

    private void login() throws Exception {
        char[] password = PasswordStore.getPassword(tokenId);
        if (password == null) {
            return;
        }

        if (activeSession == null) {
            throw new RuntimeException("Cannot login, no session");
        }

        LOG.trace("login({})", password);
        try {
            SscdTokenUtil.login(activeSession, password);
            LOG.info("User successfully logged in");

            setTokenStatus(tokenId, TokenStatusInfo.OK);

            loadPrivateKeys();
        } catch (PKCS11Exception e) {
            setTokenStatusFromErrorCode(e.getErrorCode());
            throw e;
        }
    }

    private void logout() throws Exception {
        if (activeSession == null) {
            return;
        }

        privateKeys.clear();

        LOG.trace("logout()");
        try {
            SscdTokenUtil.logout(activeSession);
            LOG.info("User successfully logged out");

            setTokenStatus(tokenId, TokenStatusInfo.OK);
        } catch (PKCS11Exception e) {
            setTokenStatusFromErrorCode(e.getErrorCode());
            throw e;
        }
    }

    private void createSession() throws Exception {
        closeActiveSession();

        if (getToken() != null) {
            activeSession =
                    getToken().openSession(SERIAL_SESSION, true, null, null);
        }
    }

    private void loadPrivateKeys() throws Exception {
        if (activeSession == null) {
            return;
        }

        privateKeys.clear();

        List<RSAPrivateKey> keysOnToken = findPrivateKeys(activeSession);
        if (keysOnToken.size() > 0) {
            LOG.trace("Found {} private key(s) on token '{}'",
                    keysOnToken.size(), getWorkerId());
        }

        for (RSAPrivateKey keyOnToken: keysOnToken) {
            String keyId = keyId(keyOnToken);
            privateKeys.put(keyId, keyOnToken);

            LOG.trace("Private key '{}' added to token '{}'", keyId,
                    getWorkerId());

            if (hasKey(keyId)) {
                setKeyAvailable(keyId, true);

                LOG.debug("Private key ({}) found in token '{}'", keyId,
                        getWorkerId());
            }
        }

        for (KeyInfo keyInfo: listKeys(tokenId)) {
            String keyId = keyInfo.getId();
            if (!privateKeys.containsKey(keyId)) {
                setKeyAvailable(keyId, false);

                LOG.debug("Private key ({}) not found in token '{}'", keyId,
                        getWorkerId());
            }
        }

        if (privateKeys.isEmpty()) {
            throw new RuntimeException("No private key(s) found on token '"
                    + getWorkerId() + "'");
        }
    }

    private void updateTokenInfo() throws Exception {
        if (getToken() == null) {
            return;
        }

        Map<String, String> tokenInfo = new HashMap<>();
        SscdTokenInfo.fillInTokenInfo(getToken().getTokenInfo(), tokenInfo);

        setTokenInfo(tokenId, tokenInfo);
    }

    private void closeActiveSession() throws Exception {
        if (activeSession != null) {
            try {
                logout();
            } finally  {
                activeSession.closeSession();
                activeSession = null;
            }
        }
    }

    private Token getToken() {
        return tokenType.getToken();
    }

    private void setTokenStatusFromErrorCode(long errorCode) {
        TokenStatusInfo status = getTokenStatusFromErrorCode(errorCode);
        if (status != null) {
            setTokenStatus(tokenId, status);
        }
    }

    private static boolean hasCert(KeyInfo key, byte[] certBytes) {
        for (CertificateInfo certInfo : key.getCerts()) {
            if (Arrays.equals(certBytes, certInfo.getCertificateBytes())) {
                return true;
            }
        }

        return false;
    }
}
