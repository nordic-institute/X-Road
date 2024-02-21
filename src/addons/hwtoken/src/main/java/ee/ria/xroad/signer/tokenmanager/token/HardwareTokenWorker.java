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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.PasswordStore;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.dto.TokenStatusInfo;
import ee.ria.xroad.signer.tokenmanager.TokenManager;
import ee.ria.xroad.signer.tokenmanager.module.ModuleConf;
import ee.ria.xroad.signer.util.SignerUtil;

import iaik.pkcs.pkcs11.Mechanism;
import iaik.pkcs.pkcs11.Session;
import iaik.pkcs.pkcs11.Token;
import iaik.pkcs.pkcs11.objects.KeyPair;
import iaik.pkcs.pkcs11.objects.RSAPrivateKey;
import iaik.pkcs.pkcs11.objects.RSAPublicKey;
import iaik.pkcs.pkcs11.objects.X509PublicKeyCertificate;
import iaik.pkcs.pkcs11.parameters.RSAPkcsPssParameters;
import iaik.pkcs.pkcs11.wrapper.PKCS11Constants;
import iaik.pkcs.pkcs11.wrapper.PKCS11Exception;
import jakarta.xml.bind.DatatypeConverter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.niis.xroad.signer.proto.ActivateTokenReq;
import org.niis.xroad.signer.proto.GenerateKeyReq;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.security.PublicKey;
import java.security.cert.CertPath;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_KEY_NOT_FOUND;
import static ee.ria.xroad.common.ErrorCodes.X_TOKEN_READONLY;
import static ee.ria.xroad.common.ErrorCodes.X_UNSUPPORTED_SIGN_ALGORITHM;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.util.CryptoUtils.calculateDigest;
import static ee.ria.xroad.common.util.CryptoUtils.encodeBase64;
import static ee.ria.xroad.common.util.CryptoUtils.getDigestAlgorithmId;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;
import static ee.ria.xroad.signer.tokenmanager.TokenManager.addCert;
import static ee.ria.xroad.signer.tokenmanager.TokenManager.addKey;
import static ee.ria.xroad.signer.tokenmanager.TokenManager.getKeyInfo;
import static ee.ria.xroad.signer.tokenmanager.TokenManager.isTokenAvailable;
import static ee.ria.xroad.signer.tokenmanager.TokenManager.listKeys;
import static ee.ria.xroad.signer.tokenmanager.TokenManager.setKeyAvailable;
import static ee.ria.xroad.signer.tokenmanager.TokenManager.setPublicKey;
import static ee.ria.xroad.signer.tokenmanager.TokenManager.setTokenActive;
import static ee.ria.xroad.signer.tokenmanager.TokenManager.setTokenAvailable;
import static ee.ria.xroad.signer.tokenmanager.TokenManager.setTokenInfo;
import static ee.ria.xroad.signer.tokenmanager.TokenManager.setTokenStatus;
import static ee.ria.xroad.signer.tokenmanager.token.HardwareTokenUtil.findPrivateKey;
import static ee.ria.xroad.signer.tokenmanager.token.HardwareTokenUtil.findPrivateKeys;
import static ee.ria.xroad.signer.tokenmanager.token.HardwareTokenUtil.findPublicKey;
import static ee.ria.xroad.signer.tokenmanager.token.HardwareTokenUtil.findPublicKeyCertificates;
import static ee.ria.xroad.signer.tokenmanager.token.HardwareTokenUtil.findPublicKeys;
import static ee.ria.xroad.signer.tokenmanager.token.HardwareTokenUtil.generateX509PublicKey;
import static ee.ria.xroad.signer.tokenmanager.token.HardwareTokenUtil.getTokenStatus;
import static ee.ria.xroad.signer.tokenmanager.token.HardwareTokenUtil.setPrivateKeyAttributes;
import static ee.ria.xroad.signer.tokenmanager.token.HardwareTokenUtil.setPublicKeyAttributes;
import static ee.ria.xroad.signer.util.ExceptionHelper.certWithIdNotFound;
import static ee.ria.xroad.signer.util.ExceptionHelper.loginFailed;
import static ee.ria.xroad.signer.util.ExceptionHelper.logoutFailed;
import static ee.ria.xroad.signer.util.SignerUtil.keyId;
import static iaik.pkcs.pkcs11.Token.SessionType.SERIAL_SESSION;

/**
 * Token worker for hardware tokens.
 */
@Slf4j
public class HardwareTokenWorker extends AbstractTokenWorker {

    private static final Mechanism KEYGEN_MECHANISM = Mechanism.get(PKCS11Constants.CKM_RSA_PKCS_KEY_PAIR_GEN);

    private final TokenType tokenType;

    // maps signature algorithm id and signing mechanism
    private final Map<String, Mechanism> signMechanisms;

    // maps key id (hex) to RSAPrivateKey
    private final Map<String, RSAPrivateKey> privateKeys = new HashMap<>();
    private final Map<String, List<X509PublicKeyCertificate>> certs = new HashMap<>();

    private Session activeSession;

    /**
     * @param tokenInfo the token info
     * @param tokenType the token type
     */
    public HardwareTokenWorker(TokenInfo tokenInfo, TokenType tokenType) {
        super(tokenInfo);

        this.tokenType = tokenType;
        this.signMechanisms = createSignMechanisms(tokenType.getSignMechanismName());
    }

    private static Map<String, Mechanism> createSignMechanisms(String signMechanismName) {
        Map<String, Mechanism> mechanismsByHashAlgorithmId = new HashMap<>();

        if (PKCS11Constants.NAME_CKM_RSA_PKCS.equals(signMechanismName)) {
            Mechanism mechanism = Mechanism.get(ModuleConf.getSupportedSignMechanismCode(signMechanismName));

            mechanismsByHashAlgorithmId.put(CryptoUtils.SHA1WITHRSA_ID, mechanism);
            mechanismsByHashAlgorithmId.put(CryptoUtils.SHA256WITHRSA_ID, mechanism);
            mechanismsByHashAlgorithmId.put(CryptoUtils.SHA384WITHRSA_ID, mechanism);
            mechanismsByHashAlgorithmId.put(CryptoUtils.SHA512WITHRSA_ID, mechanism);
        } else if (PKCS11Constants.NAME_CKM_RSA_PKCS_PSS.equals(signMechanismName)) {
            mechanismsByHashAlgorithmId.put(CryptoUtils.SHA256WITHRSAANDMGF1_ID,
                    createRsaPkcsPssMechanism(PKCS11Constants.CKM_SHA256));
            mechanismsByHashAlgorithmId.put(CryptoUtils.SHA384WITHRSAANDMGF1_ID,
                    createRsaPkcsPssMechanism(PKCS11Constants.CKM_SHA384));
            mechanismsByHashAlgorithmId.put(CryptoUtils.SHA512WITHRSAANDMGF1_ID,
                    createRsaPkcsPssMechanism(PKCS11Constants.CKM_SHA512));
        } else {
            throw new IllegalArgumentException("Not supported sign mechanism: " + signMechanismName);
        }

        return mechanismsByHashAlgorithmId;
    }

    private static Mechanism createRsaPkcsPssMechanism(long hashMechanism) {
        Mechanism mechanism = Mechanism.get(PKCS11Constants.CKM_RSA_PKCS_PSS);

        Mechanism hashAlgorithm = Mechanism.get(hashMechanism);
        long maskGenerationFunction;
        long saltLength;

        if (hashMechanism == PKCS11Constants.CKM_SHA512) {
            maskGenerationFunction = RSAPkcsPssParameters.MessageGenerationFunctionType.SHA512;
            saltLength = CryptoUtils.SHA512_DIGEST_LENGTH;
        } else if (hashMechanism == PKCS11Constants.CKM_SHA384) {
            maskGenerationFunction = RSAPkcsPssParameters.MessageGenerationFunctionType.SHA384;
            saltLength = CryptoUtils.SHA384_DIGEST_LENGTH;
        } else if (hashMechanism == PKCS11Constants.CKM_SHA256) {
            maskGenerationFunction = RSAPkcsPssParameters.MessageGenerationFunctionType.SHA256;
            saltLength = CryptoUtils.SHA256_DIGEST_LENGTH;
        } else {
            throw new IllegalArgumentException("Not supported hash mechanism");
        }

        mechanism.setParameters(new RSAPkcsPssParameters(hashAlgorithm, maskGenerationFunction, saltLength));

        return mechanism;
    }

    @Override
    public void start() {
        log.trace("start()");
        try {
            initialize();
            setTokenAvailable(tokenId, true);
        } catch (Exception e) {
            setTokenAvailable(tokenId, false);

            log.error("Error initializing token ({})", getWorkerId(), e);

            return;
        }

        try {
            login();
        } catch (Exception e) {
            log.error("Failed to log in to token '{}' at initialization", getWorkerId(), e);
        }
    }

    @Override
    public void stop() {
        super.stop();

        try {
            closeActiveSession();
        } catch (Exception e) {
            log.warn("Failed to close active session", e);
        }
    }

    @Override
    public void reload() {
        start();
    }

    @Override
    public void refresh() throws Exception {
        log.trace("refresh()");

        if (isTokenAvailable(tokenId) && activeSession != null) {
            findKeysNotInConf();
            findPublicKeysForPrivateKeys();
            findCertificatesNotInConf();
        }
    }

    @Override
    public void onActionHandled() {
        updateTokenInfo();
    }

    // ----------------------- Message handlers -------------------------------

    @Override
    protected void activateToken(ActivateTokenReq message) {
        if (message.getActivate()) { // login
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
    protected GenerateKeyResult generateKey(GenerateKeyReq message) throws Exception {
        log.trace("generateKeys()");

        assertTokenWritable();
        assertActiveSession();

        byte[] id = SignerUtil.generateId();
        // XXX maybe use: byte[] id = activeSession.generateRandom(RANDOM_ID_LENGTH);

        RSAPublicKey rsaPublicKeyTemplate = new RSAPublicKey();
        rsaPublicKeyTemplate.getId().setByteArrayValue(id);
        rsaPublicKeyTemplate.getLabel().setCharArrayValue(message.getKeyLabel().toCharArray());
        setPublicKeyAttributes(rsaPublicKeyTemplate, tokenType.getPubKeyAttributes());

        RSAPrivateKey rsaPrivateKeyTemplate = new RSAPrivateKey();
        rsaPrivateKeyTemplate.getId().setByteArrayValue(id);
        rsaPrivateKeyTemplate.getLabel().setCharArrayValue(message.getKeyLabel().toCharArray());
        setPrivateKeyAttributes(rsaPrivateKeyTemplate, tokenType.getPrivKeyAttributes());

        KeyPair generatedKP = activeSession.generateKeyPair(KEYGEN_MECHANISM, rsaPublicKeyTemplate,
                rsaPrivateKeyTemplate);

        RSAPrivateKey privateKey = (RSAPrivateKey) generatedKP.getPrivateKey();

        if (privateKey == null) {
            throw new CodedException(X_INTERNAL_ERROR, "Could not generate private key");
        }

        RSAPublicKey publicKey = (RSAPublicKey) generatedKP.getPublicKey();

        if (publicKey == null) {
            throw new CodedException(X_INTERNAL_ERROR, "Could not generate public key");
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

        RSAPrivateKey privateKey = getPrivateKey(keyId);

        if (privateKey != null) {
            log.info("Deleting private key '{}' on token '{}'", keyId, getWorkerId());

            try {
                activeSession.destroyObject(privateKey);
                privateKeys.remove(keyId);
            } catch (Exception e) {
                throw new CodedException(X_INTERNAL_ERROR, "Failed to delete private key '%s' on token '%s': %s",
                        keyId, getWorkerId(), e);
            }
        } else {
            log.warn("Could not find private key '{}' on token '{}'", keyId, getWorkerId());
        }

        RSAPublicKey publicKey = findPublicKey(activeSession, keyId,
                tokenType.getPubKeyAttributes().getAllowedMechanisms());

        if (publicKey != null) {
            log.info("Deleting public key '{}' on token '{}'", keyId, getWorkerId());

            try {
                activeSession.destroyObject(publicKey);
            } catch (Exception e) {
                throw new CodedException(X_INTERNAL_ERROR, "Failed to delete public key '%s' on token '%s': %s",
                        keyId, getWorkerId(), e);
            }
        } else {
            log.warn("Could not find public key '{}' on token '{}'", keyId, getWorkerId());
        }

        if (!certs.containsKey(keyId)) {
            return;
        }

        certs.get(keyId).forEach(this::destroyCert);
        certs.remove(keyId);
    }

    @Override
    protected void deleteCert(String certId) {
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
                List<X509PublicKeyCertificate> certsOnModule = certs.get(keyInfo.getId());

                for (X509PublicKeyCertificate cert : certsOnModule) {
                    if (Arrays.equals(certInfo.getCertificateBytes(), cert.getValue().getByteArrayValue())) {
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
    protected byte[] sign(String keyId, String signatureAlgorithmId, byte[] data) throws Exception {
        log.trace("sign({}, {})", keyId, signatureAlgorithmId);

        assertActiveSession();
        pinVerificationPerSigningLogin();
        assertKeyAvailable(keyId);

        RSAPrivateKey key = getPrivateKey(keyId);
        if (key == null) {
            throw CodedException.tr(X_KEY_NOT_FOUND, "key_not_found_on_token", "Key '%s' not found on token '%s'",
                    keyId, tokenId);
        }

        log.debug("Signing with key '{}' and signature algorithm '{}'", keyId, signatureAlgorithmId);
        try {
            Mechanism signMechanism = signMechanisms.get(signatureAlgorithmId);
            if (signMechanism == null) {
                throw CodedException.tr(X_UNSUPPORTED_SIGN_ALGORITHM, "unsupported_sign_algorithm",
                        "Unsupported signature algorithm '%s'", signatureAlgorithmId);
            }
            activeSession.signInit(signMechanism, key);
            return activeSession.sign(data);
        } finally {
            pinVerificationPerSigningLogout();
        }
    }

    protected byte[] signCertificate(String keyId, String signatureAlgorithmId, String subjectName, PublicKey publicKey) throws Exception {
        log.trace("signCertificate({}, {}, {})", keyId, signatureAlgorithmId, subjectName);

        assertKeyAvailable(keyId);
        KeyInfo keyInfo = getKeyInfo(keyId);
        CertificateInfo certificateInfo = keyInfo.getCerts().get(0);
        X509Certificate issuerX509Certificate = readCertificate(certificateInfo.getCertificateBytes());

        ContentSigner contentSigner = new HardwareTokenContentSigner(keyId, signatureAlgorithmId);

        JcaX509v3CertificateBuilder certificateBuilder = getCertificateBuilder(subjectName, publicKey,
                issuerX509Certificate);
        X509CertificateHolder certHolder = certificateBuilder.build(contentSigner);
        X509Certificate signedCert = new JcaX509CertificateConverter().getCertificate(certHolder);

        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509", "BC");
        CertPath certPath = certificateFactory.generateCertPath(Arrays.asList(signedCert, issuerX509Certificate));
        return certPath.getEncoded("PEM");
    }


    // ------------------------------------------------------------------------

    private void findKeysNotInConf() throws Exception {
        log.trace("findKeysNotInConf()");

        try {
            List<RSAPublicKey> keysOnToken = findPublicKeys(activeSession,
                    tokenType.getPubKeyAttributes().getAllowedMechanisms());

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

                    log.debug("Found new key with id '{}' on token '{}'", keyId, getWorkerId());
                }

                // update the key label
                char[] label = keyOnToken.getLabel().getCharArrayValue();

                if (label != null) {
                    TokenManager.setKeyLabel(keyId, new String(label));
                }

                if (key.getPublicKey() == null) {
                    updatePublicKey(keyId);
                }
            }
        } catch (PKCS11Exception e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to find keys from token '{}'", getWorkerId(), e);
        }
    }

    private RSAPrivateKey getPrivateKey(String keyId) throws Exception {
        RSAPrivateKey privateKey = privateKeys.get(keyId);
        if (privateKey == null) {
            log.debug("Key {} not found in cache, trying to find it from hardware token", keyId);
            privateKey = findPrivateKey(activeSession, keyId, tokenType.getPrivKeyAttributes().getAllowedMechanisms());
            privateKeys.put(keyId, privateKey);
        }
        return privateKey;
    }

    private void findPublicKeysForPrivateKeys() throws Exception {
        log.trace("findPublicKeysForPrivateKeys()");

        for (KeyInfo key : listKeys(tokenId)) {
            if (key.getPublicKey() == null && key.getId() != null) {
                updatePublicKey(key.getId());
            }
        }
    }

    private void findCertificatesNotInConf() throws Exception {
        log.trace("findCertificatesNotInConf()");

        try {
            List<X509PublicKeyCertificate> certsOnModule = findPublicKeyCertificates(activeSession);
            List<KeyInfo> existingKeys = listKeys(tokenId);

            for (X509PublicKeyCertificate certOnModule : certsOnModule) {
                byte[] certBytes = certOnModule.getValue().getByteArrayValue();

                for (KeyInfo key : existingKeys) {
                    if (key.getId().equals(keyId(certOnModule)) && !hasCert(key, certBytes)) {
                        log.debug("Found new certificate for key '{}'", key.getId());

                        addCert(key.getId(), certBytes);
                        putCert(key.getId(), certOnModule);
                    }
                }
            }
        } catch (PKCS11Exception e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to find certificates not in conf", e);
        }
    }

    private void updatePublicKey(String keyId) throws Exception {
        log.trace("updatePublicKey({})", keyId);

        try {
            String publicKeyBase64 = null;

            RSAPublicKey publicKey = findPublicKey(activeSession, keyId,
                    tokenType.getPubKeyAttributes().getAllowedMechanisms());

            if (publicKey != null) {
                publicKeyBase64 = encodeBase64(generateX509PublicKey(publicKey));
                setPublicKey(keyId, publicKeyBase64);
            } else if (certs.containsKey(keyId) && !certs.get(keyId).isEmpty()) {
                X509PublicKeyCertificate first = certs.get(keyId).get(0);
                X509Certificate cert = readCertificate(first.getValue().getByteArrayValue());
                publicKeyBase64 = encodeBase64(cert.getPublicKey().getEncoded());
            }

            if (publicKeyBase64 != null) {
                log.debug("Found public key for key '{}'...", keyId);

                setPublicKey(keyId, publicKeyBase64);
            }
        } catch (PKCS11Exception e) {
            throw e;
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

    private void pinVerificationPerSigningLogin() {
        if (tokenType.isPinVerificationPerSigning()) {
            try {
                login();
            } catch (Exception e) {
                log.warn("Login failed", e);

                throw loginFailed(e.getMessage());
            }
        }
    }

    private void pinVerificationPerSigningLogout() {
        if (tokenType.isPinVerificationPerSigning()) {
            try {
                logout();
            } catch (Exception e) {
                log.error("Logout failed", e);
            }
        }
    }

    private void createSession() throws Exception {
        log.trace("createSession()");
        closeActiveSession();

        if (getToken() != null) {
            activeSession = getToken().openSession(SERIAL_SESSION, true, null, null);
        }
    }

    private void loadPrivateKeys() throws Exception {
        if (activeSession == null) {
            return;
        }

        privateKeys.clear();

        List<RSAPrivateKey> keysOnToken = findPrivateKeys(activeSession,
                tokenType.getPrivKeyAttributes().getAllowedMechanisms());

        log.trace("Found {} private key(s) on token '{}'", keysOnToken.size(), getWorkerId());

        for (RSAPrivateKey keyOnToken : keysOnToken) {
            String keyId = keyId(keyOnToken);

            if (keyId == null) {
                log.debug("Ignoring private key with no ID");

                continue;
            }

            privateKeys.put(keyId, keyOnToken);

            log.trace("Private key '{}' added to token '{}'", keyId, getWorkerId());

            if (!hasKey(keyId)) {
                addKey(tokenId, keyId, null);
            } else {
                log.debug("Private key ({}) found in token '{}'", keyId, getWorkerId());
            }

            setKeyAvailable(keyId, true);
        }

        for (KeyInfo keyInfo : listKeys(tokenId)) {
            String keyId = keyInfo.getId();

            if (!privateKeys.containsKey(keyId)) {
                setKeyAvailable(keyId, false);

                log.debug("Private key ({}) not found in token '{}'", keyId, getWorkerId());
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
            HardwareTokenInfo.fillInTokenInfo(getToken().getTokenInfo(), tokenInfo);

            setTokenInfo(tokenId, tokenInfo);
        } catch (Exception e) {
            log.error("Failed to update token info", e);
        }
    }

    private void closeActiveSession() throws Exception {
        if (activeSession != null) {
            try {
                logout();
            } finally {
                activeSession.closeSession();
                activeSession = null;
            }
        }
    }

    private Token getToken() {
        return ((HardwareTokenType) tokenType).getToken();
    }

    private void setTokenStatusFromErrorCode(long errorCode) throws Exception {
        TokenStatusInfo status = getTokenStatus(getToken().getTokenInfo(), errorCode);

        if (status != null) {
            setTokenStatus(tokenId, status);
        }
    }

    private void assertActiveSession() {
        if (activeSession == null) {
            throw new CodedException(X_INTERNAL_ERROR, "No active session on token %s", tokenId);
        }
    }

    private void assertTokenWritable() {
        if (tokenType.isReadOnly()) {
            throw CodedException.tr(X_TOKEN_READONLY, "token_is_readonly", "Token '%s' is read only", tokenId);
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
            log.error("Failed to delete certificate on token '{}'", getWorkerId(), e);
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

    @Override
    public boolean isSoftwareToken() {
        return false;
    }

    @Override
    public void handleUpdateTokenPin(char[] oldPin, char[] newPin) {
        //NO-OP
    }

    @Override
    public void initializeToken(char[] pin) {
        //NO-OP
    }

    private class HardwareTokenContentSigner implements ContentSigner {

        private final ByteArrayOutputStream out;
        private final String keyId;
        private final String signatureAlgorithmId;

        HardwareTokenContentSigner(String keyId, String signatureAlgorithmId) {
            this.keyId = keyId;
            this.signatureAlgorithmId = signatureAlgorithmId;
            out = new ByteArrayOutputStream();
        }

        @Override
        public byte[] getSignature() {
            try {
                assertActiveSession();
                pinVerificationPerSigningLogin();
                byte[] dataToSign = out.toByteArray();
                RSAPrivateKey privateKey = getPrivateKey(keyId);
                if (privateKey == null) {
                    throw CodedException.tr(X_KEY_NOT_FOUND, "key_not_found_on_token", "Key '%s' not found on token '%s'",
                            keyId, tokenId);
                }
                log.debug("Signing with key '{}' and signature algorithm '{}'", keyId, signatureAlgorithmId);
                Mechanism signatureMechanism = signMechanisms.get(signatureAlgorithmId);
                if (signatureMechanism == null) {
                    throw CodedException.tr(X_UNSUPPORTED_SIGN_ALGORITHM, "unsupported_sign_algorithm",
                            "Unsupported signature algorithm '%s'", signatureAlgorithmId);
                }
                activeSession.signInit(signatureMechanism, privateKey);
                String digestAlgorithmId = getDigestAlgorithmId(signatureAlgorithmId);
                byte[] digest = calculateDigest(digestAlgorithmId, dataToSign);
                byte[] dataDigestToSign = SignerUtil.createDataToSign(digest, signatureAlgorithmId);
                return activeSession.sign(dataDigestToSign);
            } catch (Exception e) {
                log.error(e.getMessage());
                throw translateException(e);
            } finally {
                pinVerificationPerSigningLogout();
            }
        }

        @Override
        public OutputStream getOutputStream() {
            return out;
        }

        @Override
        public AlgorithmIdentifier getAlgorithmIdentifier() {
            return new DefaultSignatureAlgorithmIdentifierFinder().find(signatureAlgorithmId);
        }
    }
}
