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
package ee.ria.xroad.signer.tokenmanager.token;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.DatatypeConverter;

import iaik.pkcs.pkcs11.Mechanism;
import iaik.pkcs.pkcs11.Session;
import iaik.pkcs.pkcs11.Token;
import iaik.pkcs.pkcs11.objects.KeyPair;
import iaik.pkcs.pkcs11.objects.RSAPrivateKey;
import iaik.pkcs.pkcs11.objects.RSAPublicKey;
import iaik.pkcs.pkcs11.objects.X509PublicKeyCertificate;
import iaik.pkcs.pkcs11.wrapper.PKCS11Constants;
import iaik.pkcs.pkcs11.wrapper.PKCS11Exception;
import lombok.extern.slf4j.Slf4j;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.util.PasswordStore;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.dto.TokenStatusInfo;
import ee.ria.xroad.signer.protocol.message.ActivateToken;
import ee.ria.xroad.signer.protocol.message.GenerateKey;
import ee.ria.xroad.signer.tokenmanager.TokenManager;
import ee.ria.xroad.signer.util.SignerUtil;

import static ee.ria.xroad.common.ErrorCodes.*;
import static ee.ria.xroad.common.util.CryptoUtils.encodeBase64;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;
import static ee.ria.xroad.signer.tokenmanager.TokenManager.*;
import static ee.ria.xroad.signer.tokenmanager.token.HardwareTokenUtil.*;
import static ee.ria.xroad.signer.util.ExceptionHelper.*;
import static ee.ria.xroad.signer.util.SignerUtil.keyId;
import static iaik.pkcs.pkcs11.Token.SessionType.SERIAL_SESSION;

/**
 * Token worker for hardware tokens.
 */
@Slf4j
public class HardwareTokenWorker extends AbstractTokenWorker {

    private static final Mechanism SIGN_MECHANISM =
            Mechanism.get(PKCS11Constants.CKM_RSA_PKCS);

    private static final Mechanism KEYGEN_MECHANISM =
            Mechanism.get(PKCS11Constants.CKM_RSA_PKCS_KEY_PAIR_GEN);

    private final HardwareTokenType tokenType;

    // maps key id (hex) to RSAPrivateKey
    private final Map<String, RSAPrivateKey> privateKeys = new HashMap<>();
    private final Map<String, List<X509PublicKeyCertificate>> certs =
            new HashMap<>();

    private Session activeSession;

    /**
     * @param tokenInfo the token info
     * @param tokenType the token type
     */
    public HardwareTokenWorker(TokenInfo tokenInfo,
            HardwareTokenType tokenType) {
        super(tokenInfo);

        this.tokenType = tokenType;
    }

    @Override
    public void preStart() throws Exception {
        try {
            initialize();
            setTokenAvailable(tokenId, true);
        } catch (Exception e) {
            setTokenAvailable(tokenId, false);
            log.error("Error initializing token ({}): {}", getWorkerId(), e);
            return;
        }

        try {
            login();
        } catch (Exception e) {
            log.error("Failed to log in to token '" + getWorkerId()
                    + "' at initialization", e);
        }
    }

    @Override
    public void postStop() throws Exception {
        super.postStop();
        try {
            closeActiveSession();
        } catch (Exception e) {
            log.warn("Failed to close active session", e);
        }
    }

    @Override
    protected void onUpdate() throws Exception {
        log.trace("onUpdate()");

        if (isTokenAvailable(tokenId) && activeSession != null) {
            findKeysNotInConf();
            findPublicKeysForPrivateKeys();
            findCertificatesNotInConf();
        }
    }

    @Override
    protected void onMessage(Object message) throws Exception {
        try {
            super.onMessage(message);
        } finally {
            updateTokenInfo();
        }
    }

    @Override
    protected Exception customizeException(Exception e) {
        if (e instanceof PKCS11Exception) {
            // For some unknown reason, throwing PKCS11Exception causes an
            // association error in Akka, so that the response message is not
            // sent to the client.
            return new Exception(e.getMessage());
        }

        return e;
    }

    // ----------------------- Message handlers -------------------------------

    @Override
    protected void activateToken(ActivateToken message) throws Exception {
        if (message.isActivate()) { // login
            log.info("Logging in token '{}'", getWorkerId());
            try {
                if (activeSession == null) {
                    initialize();
                }

                login();
            } catch (Exception e) {
                log.warn("Token login failed", e);
                throw loginFailed(e.getMessage());
            }
        } else { // logout
            log.info("Logging out token '{}'", getWorkerId());
            try {
                logout();
            } catch (Exception e) {
                log.warn("Token logout failed", e);
                throw logoutFailed(e.getMessage());
            }
        }
    }

    @Override
    protected GenerateKeyResult generateKey(GenerateKey message)
            throws Exception {
        log.trace("generateKeys()");

        assertTokenWritable();
        assertActiveSession();

        byte[] id = SignerUtil.generateId();
        // XXX maybe use: byte[] id = activeSession.generateRandom(RANDOM_ID_LENGTH);

        RSAPublicKey rsaPublicKeyTemplate = new RSAPublicKey();
        rsaPublicKeyTemplate.getId().setByteArrayValue(id);
        setPublicKeyAttributes(rsaPublicKeyTemplate);

        RSAPrivateKey rsaPrivateKeyTemplate = new RSAPrivateKey();
        rsaPrivateKeyTemplate.getId().setByteArrayValue(id);
        // TODO rsaPrivateKeyTemplate.getLabel().setCharArrayValue(label);
        setPrivateKeyAttributes(rsaPrivateKeyTemplate);

        KeyPair generatedKP = activeSession.generateKeyPair(KEYGEN_MECHANISM,
                rsaPublicKeyTemplate, rsaPrivateKeyTemplate);

        RSAPrivateKey privateKey = (RSAPrivateKey) generatedKP.getPrivateKey();
        if (privateKey == null) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Could not generate private key");
        }

        RSAPublicKey publicKey = (RSAPublicKey) generatedKP.getPublicKey();
        if (publicKey == null) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Could not generate public key");
        }

        byte[] keyIdBytes = privateKey.getId().getByteArrayValue();
        String keyId = DatatypeConverter.printHexBinary(keyIdBytes);

        byte[] publicKeyBytes = generateX509PublicKey(publicKey);
        String publicKeyBase64 = encodeBase64(publicKeyBytes);

        privateKeys.put(keyId, privateKey);

        return new GenerateKeyResult(keyId, publicKeyBase64);
    }

    @Override
    protected void deleteKey(String keyId) throws Exception {
        log.trace("deleteKey({})", keyId);

        assertTokenWritable();
        assertActiveSession();

        RSAPrivateKey privateKey = privateKeys.get(keyId);
        if (privateKey != null) {
            log.info("Deleting private key '{}' on token '{}'", keyId,
                    getWorkerId());
            try {
                activeSession.destroyObject(privateKey);
                privateKeys.remove(keyId);
            } catch (Exception e) {
                throw new CodedException(X_INTERNAL_ERROR,
                        "Failed to delete private key '%s' on token '%s': %s",
                        new Object[] {keyId, getWorkerId(), e});
            }
        } else {
            log.warn("Could not find private key '{}' on token '{}'", keyId,
                    getWorkerId());
        }

        RSAPublicKey publicKey = findPublicKey(activeSession, keyId);
        if (publicKey != null) {
            log.info("Deleting public key '{}' on token '{}'", keyId,
                    getWorkerId());
            try {
                activeSession.destroyObject(publicKey);
            } catch (Exception e) {
                throw new CodedException(X_INTERNAL_ERROR,
                        "Failed to delete public key '%s' on token '%s': %s",
                        new Object[] {keyId, getWorkerId(), e});
            }
        } else {
            log.warn("Could not find public key '{}' on token '{}'", keyId,
                    getWorkerId());
        }

        if (!certs.containsKey(keyId)) {
            return;
        }

        certs.get(keyId).stream().forEach(this::destroyCert);
        certs.remove(keyId);
    }

    @Override
    protected void deleteCert(String certId) throws Exception {
        log.trace("deleteCert({})", certId);

        assertTokenWritable();
        assertActiveSession();

        KeyInfo keyInfo = TokenManager.getKeyInfoForCertId(certId);
        if (keyInfo == null) {
            throw certWithIdNotFound(certId);
        }

        if (!certs.containsKey(keyInfo.getId())) {
            return;
        }

        for (CertificateInfo certInfo : keyInfo.getCerts()) {
            if (certInfo.getId().equals(certId)) {
                List<X509PublicKeyCertificate> certsOnModule =
                        certs.get(keyInfo.getId());
                for (X509PublicKeyCertificate cert : certsOnModule) {
                    if (Arrays.equals(certInfo.getCertificateBytes(),
                            cert.getValue().getByteArrayValue())) {
                        destroyCert(cert);
                        certsOnModule.remove(cert);
                        TokenManager.removeCert(certId);
                        break;
                    }
                }

                return;
            }
        }
    }

    @Override
    protected byte[] sign(String keyId, byte[] data) throws Exception {
        log.trace("sign({})", keyId);

        assertActiveSession();

        if (tokenType.isPinVerificationPerSigning()) {
            try {
                login();
            } catch (Exception e) {
                log.warn("Login failed", e);
                throw loginFailed(e.getMessage());
            }
        }

        if (!isKeyAvailable(keyId)) {
            throw keyNotAvailable(keyId);
        }

        RSAPrivateKey key = privateKeys.get(keyId);
        if (key == null) {
            throw CodedException.tr(X_KEY_NOT_FOUND,
                    "key_not_found_on_token",
                    "Key '%s' not found on token '%s'", keyId, tokenId);
        }

        log.debug("Signing with key '{}'", keyId);
        try {
            activeSession.signInit(SIGN_MECHANISM, key);
            return activeSession.sign(data);
        } finally {
            if (tokenType.isPinVerificationPerSigning()) {
                try {
                    logout();
                } catch (Exception e) {
                    log.error("Logout failed", e);
                }
            }
        }
    }

    // ------------------------------------------------------------------------

    private void findKeysNotInConf() {
        log.trace("findKeysNotInConf()");

        try {
            List<RSAPublicKey> keysOnToken = findPublicKeys(activeSession);
            for (RSAPublicKey keyOnToken : keysOnToken) {
                String keyId = keyId(keyOnToken);
                if (keyId == null) {
                    log.debug("Ignoring public key with no ID");
                    continue;
                }

                KeyInfo key = getKeyInfo(keyId);
                if (key == null) {
                    key = addKey(tokenId, keyId, null);
                    setKeyAvailable(keyId, true);

                    log.debug("Found new key with id '{}' on token '{}'",
                            keyId, getWorkerId());
                }

                if (key.getPublicKey() == null) {
                    updatePublicKey(keyId);
                }
            }
        } catch (Exception e) {
            log.error("Failed to find keys from token '{}': {}",
                    getWorkerId(), e);
        }
    }

    private void findPublicKeysForPrivateKeys() throws Exception {
        log.trace("findPublicKeysForPrivateKeys()");

        for (KeyInfo key : listKeys(tokenId)) {
            if (key.getPublicKey() == null && key.getId() != null) {
                updatePublicKey(key.getId());
            }
        }
    }

    private void findCertificatesNotInConf() {
        log.trace("findCertificatesNotInConf()");

        try {
            List<X509PublicKeyCertificate> certsOnModule =
                    findPublicKeyCertificates(activeSession);
            List<KeyInfo> existingKeys = listKeys(tokenId);
            for (X509PublicKeyCertificate certOnModule : certsOnModule) {
                byte[] certBytes = certOnModule.getValue().getByteArrayValue();
                for (KeyInfo key : existingKeys) {
                    if (key.getId().equals(keyId(certOnModule))
                            && !hasCert(key, certBytes)) {
                        log.debug("Found new certificate for key '{}'",
                                key.getId());

                        addCert(key.getId(), certBytes);
                        putCert(key.getId(), certOnModule);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to find certificates not in conf", e);
        }
    }

    private void updatePublicKey(String keyId) {
        log.trace("updatePublicKey({})", keyId);

        try {
            String publicKeyBase64 = null;

            RSAPublicKey publicKey = findPublicKey(activeSession, keyId);
            if (publicKey != null) {
                publicKeyBase64 =
                        encodeBase64(generateX509PublicKey(publicKey));
                setPublicKey(keyId, publicKeyBase64);
            } else if (certs.containsKey(keyId)
                    && !certs.get(keyId).isEmpty()) {
                X509PublicKeyCertificate first = certs.get(keyId).get(0);
                X509Certificate cert =
                        readCertificate(first.getValue().getByteArrayValue());
                publicKeyBase64 =
                        encodeBase64(cert.getPublicKey().getEncoded());
            }

            if (publicKeyBase64 != null) {
                log.debug("Found public key for key '{}'...", keyId);

                setPublicKey(keyId, publicKeyBase64);
            }
        } catch (Exception e) {
            log.error("Failed to find public key for key " + keyId, e);
        }
    }

    // ------------------------------------------------------------------------

    private void initialize() throws Exception {
        log.trace("initialize()");

        createSession();
        updateTokenInfo();
    }

    private void login() throws Exception {
        char[] password = PasswordStore.getPassword(tokenId);
        if (password == null) {
            log.debug("Cannot login, no password stored");
            return;
        }

        assertActiveSession();

        log.trace("login()");
        try {
            HardwareTokenUtil.login(activeSession, password);
            log.info("User successfully logged in");

            setTokenStatus(tokenId, TokenStatusInfo.OK);
            setTokenActive(tokenId, true);
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

        log.trace("logout()");
        try {
            HardwareTokenUtil.logout(activeSession);
            log.info("User successfully logged out");

            setTokenStatus(tokenId, TokenStatusInfo.OK);
        } catch (PKCS11Exception e) {
            setTokenStatusFromErrorCode(e.getErrorCode());
            throw e;
        } finally {
            setTokenActive(tokenId, false);
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
        log.trace("Found {} private key(s) on token '{}'", keysOnToken.size(),
                getWorkerId());

        for (RSAPrivateKey keyOnToken: keysOnToken) {
            String keyId = keyId(keyOnToken);
            if (keyId == null) {
                log.debug("Ignoring private key with no ID");
                continue;
            }

            privateKeys.put(keyId, keyOnToken);

            log.trace("Private key '{}' added to token '{}'", keyId,
                    getWorkerId());

            if (!hasKey(keyId)) {
                addKey(tokenId, keyId, null);
            } else {
                log.debug("Private key ({}) found in token '{}'", keyId,
                        getWorkerId());
            }

            setKeyAvailable(keyId, true);
        }

        for (KeyInfo keyInfo: listKeys(tokenId)) {
            String keyId = keyInfo.getId();
            if (!privateKeys.containsKey(keyId)) {
                setKeyAvailable(keyId, false);

                log.debug("Private key ({}) not found in token '{}'", keyId,
                        getWorkerId());
            }
        }

        if (privateKeys.isEmpty()) {
            log.warn("No private key(s) found in token '{}'", getWorkerId());
        }
    }

    private void updateTokenInfo() {
        if (getToken() == null) {
            return;
        }

        try {
            Map<String, String> tokenInfo = new HashMap<>();
            HardwareTokenInfo.fillInTokenInfo(getToken().getTokenInfo(),
                    tokenInfo);

            setTokenInfo(tokenId, tokenInfo);
        } catch (Exception e) {
            log.error("Failed to update token info", e);
        }
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

    private void setTokenStatusFromErrorCode(long errorCode) throws Exception {
        TokenStatusInfo status =
                getTokenStatus(getToken().getTokenInfo(), errorCode);
        if (status != null) {
            setTokenStatus(tokenId, status);
        }
    }

    private void assertActiveSession() {
        if (activeSession == null) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "No active session on token %s", tokenId);
        }
    }

    private void assertTokenWritable() {
        if (tokenType.isReadOnly()) {
            throw CodedException.tr(X_TOKEN_READONLY,
                    "token_is_readonly", "Token '%s' is read only", tokenId);
        }
    }

    private void putCert(String keyId, X509PublicKeyCertificate cert) {
        if (certs.get(keyId) == null) {
            List<X509PublicKeyCertificate> certsOfKey = new ArrayList<>();
            certsOfKey.add(cert);
            certs.put(keyId, certsOfKey);
        } else {
            certs.get(keyId).add(cert);
        }
    }

    private void destroyCert(X509PublicKeyCertificate cert) {
        try {
            activeSession.destroyObject(cert);
        } catch (Exception e) {
            log.error("Failed to delete certificate on token '{}': {}",
                    new Object[] {getWorkerId(), e});
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
